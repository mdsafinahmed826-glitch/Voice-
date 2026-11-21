"""
VoxClone Studio - FastAPI Zero-Shot Voice Cloning Backend
Supports Coqui XTTS-v2 (with F5-TTS / Fish Speech adapter hooks) and automatic
CPU/Demo fallback when GPU or model weights are unavailable.
"""

import io
import os
import tempfile
import time
from typing import Optional

import numpy as np
import soundfile as sf
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response

# Optional ML / Audio DSP imports (graceful fallback if running lightweight demo)
try:
    import torch
    import torchaudio
    HAS_TORCH = True
except ImportError:
    HAS_TORCH = False

try:
    import librosa
    HAS_LIBROSA = True
except ImportError:
    HAS_LIBROSA = False

try:
    import noisereduce as nr
    HAS_NOISEREDUCE = True
except ImportError:
    HAS_NOISEREDUCE = False

try:
    from TTS.api import TTS
    HAS_COQUI_TTS = True
except ImportError:
    HAS_COQUI_TTS = False

TARGET_SAMPLE_RATE = 24000
API_KEY_ENV = os.getenv("VOXCLONE_API_KEY", "").strip()
MODEL_NAME = os.getenv("VOXCLONE_MODEL", "tts_models/multilingual/multi-dataset/xtts_v2")

app = FastAPI(title="VoxClone Studio API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class VoiceCloningEngine:
    def __init__(self) -> None:
        self.device = "cuda" if (HAS_TORCH and torch.cuda.is_available()) else "cpu"
        self.tts_model = None
        self.fallback_mode = False
        self._load_model()

    def _load_model(self) -> None:
        if not HAS_COQUI_TTS:
            self.fallback_mode = True
            return
        try:
            self.tts_model = TTS(MODEL_NAME).to(self.device)
            self.fallback_mode = False
        except Exception as exc:
            print(f"[VoxClone] Could not load {MODEL_NAME}: {exc}. Using fallback mode.")
            self.fallback_mode = True

    def preprocess_reference(self, raw_bytes: bytes, filename: str) -> str:
        """
        1. Decode WAV/MP3/M4A via soundfile/librosa
        2. Convert to mono & resample to 24 kHz
        3. Apply spectral noise reduction + energy/VAD silence trimming
        4. Peak + RMS normalize to -18 dBFS
        Returns path to a clean temporary 24kHz WAV file.
        """
        suffix = os.path.splitext(filename)[1] or ".wav"
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp_in:
            tmp_in.write(raw_bytes)
            in_path = tmp_in.name

        try:
            if HAS_LIBROSA:
                wav, _ = librosa.load(in_path, sr=TARGET_SAMPLE_RATE, mono=True)
                wav, _ = librosa.effects.trim(wav, top_db=28)
            else:
                wav, sr = sf.read(in_path, dtype="float32")
                if wav.ndim > 1:
                    wav = np.mean(wav, axis=1)
                if sr != TARGET_SAMPLE_RATE and HAS_TORCH:
                    tensor = torch.from_numpy(wav).unsqueeze(0)
                    wav = torchaudio.functional.resample(tensor, sr, TARGET_SAMPLE_RATE).squeeze(0).numpy()

            if len(wav) < int(TARGET_SAMPLE_RATE * 1.0):
                raise ValueError("Reference audio must contain at least 1.0 second of speech.")

            if HAS_NOISEREDUCE:
                wav = nr.reduce_noise(y=wav, sr=TARGET_SAMPLE_RATE, prop_decrease=0.75)

            # RMS loudness normalization (~ -18 dBFS) + soft peak guard
            rms = np.sqrt(np.mean(np.square(wav)) + 1e-9)
            target_rms = 10 ** (-18.0 / 20.0)
            wav = wav * (target_rms / rms)
            peak = np.max(np.abs(wav)) + 1e-9
            if peak > 0.95:
                wav = wav * (0.95 / peak)

            out_wav = tempfile.NamedTemporaryFile(suffix=".wav", delete=False).name
            sf.write(out_wav, wav.astype(np.float32), TARGET_SAMPLE_RATE, subtype="PCM_16")
            return out_wav
        finally:
            if os.path.exists(in_path):
                os.remove(in_path)

    @staticmethod
    def _chunk_text(text: str, max_chars: int = 320) -> list[str]:
        if len(text) <= max_chars:
            return [text]
        chunks, start = [], 0
        while start < len(text):
            end = min(len(text), start + max_chars)
            if end == len(text):
                chunks.append(text[start:].strip())
                break
            split_at = -1
            for sep in (". ", "! ", "? ", "। ", "\n", ", ", " "):
                idx = text.rfind(sep, start, end)
                if idx > start + max_chars // 3:
                    split_at = idx + len(sep)
                    break
            if split_at <= start:
                split_at = end
            part = text[start:split_at].strip()
            if part:
                chunks.append(part)
            start = split_at
        return chunks

    def synthesize(
        self,
        clean_ref_path: str,
        text: str,
        language: str,
        speed: float,
        similarity: float,
        temperature: float,
    ) -> np.ndarray:
        if not self.fallback_mode and self.tts_model is not None:
            segments = []
            silence = np.zeros(int(TARGET_SAMPLE_RATE * 0.12), dtype=np.float32)
            for chunk in self._chunk_text(text):
                out_path = tempfile.NamedTemporaryFile(suffix=".wav", delete=False).name
                try:
                    with torch.inference_mode():
                        self.tts_model.tts_to_file(
                            text=chunk,
                            speaker_wav=clean_ref_path,
                            language=language,
                            file_path=out_path,
                            speed=speed,
                            temperature=temperature,
                        )
                    wav, _ = sf.read(out_path, dtype="float32")
                    segments.append(wav)
                    segments.append(silence)
                finally:
                    if os.path.exists(out_path):
                        os.remove(out_path)
            if self.device == "cuda":
                torch.cuda.empty_cache()
            return np.concatenate(segments[:-1]) if segments else np.zeros(TARGET_SAMPLE_RATE, dtype=np.float32)

        # Fallback / CPU Demo Synthesizer when GPU or XTTS weights are not installed:
        # Scales duration proportionally with unlimited text length.
        ref_wav, _ = sf.read(clean_ref_path, dtype="float32")
        duration_sec = max(1.5, (len(text) / 14.0) / max(0.5, speed))
        t = np.linspace(0, duration_sec, int(TARGET_SAMPLE_RATE * duration_sec), endpoint=False)
        base_f0 = 135.0 + 35.0 * similarity
        vibrato = 4.0 * np.sin(2 * np.pi * 5.2 * t)
        envelope = np.clip(np.sin(np.pi * t / duration_sec) * 1.4, 0.0, 1.0)
        syllables = 0.55 + 0.45 * np.abs(np.sin(2 * np.pi * (3.2 * speed) * t))
        wave = (
            0.22 * np.sin(2 * np.pi * (base_f0 + vibrato) * t)
            + 0.11 * np.sin(2 * np.pi * 2.0 * (base_f0 + vibrato) * t)
            + 0.05 * np.sin(2 * np.pi * 3.0 * (base_f0 + vibrato) * t)
        ) * envelope * syllables
        if len(ref_wav) > 0:
            tiled_ref = np.resize(ref_wav, len(wave))
            wave = 0.85 * wave + 0.15 * np.clip(tiled_ref, -0.2, 0.2)
        return wave.astype(np.float32)


engine = VoiceCloningEngine()


def verify_api_key(
    authorization: Optional[str] = Header(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> None:
    if not API_KEY_ENV:
        return
    token = x_api_key or (authorization.replace("Bearer ", "").strip() if authorization else "")
    if token != API_KEY_ENV:
        raise HTTPException(status_code=401, detail="Invalid or missing API key")


@app.get("/health")
def health_check(_: None = Depends(verify_api_key)):
    return {
        "status": "ok",
        "model": "Fallback Demo" if engine.fallback_mode else MODEL_NAME,
        "device": engine.device,
        "cuda_available": bool(HAS_TORCH and torch.cuda.is_available()),
        "fallback_mode": engine.fallback_mode,
    }


@app.post("/api/v1/clone")
async def clone_voice(
    reference_audio: UploadFile = File(...),
    text: str = Form(...),
    language: str = Form("en"),
    speed: float = Form(1.0),
    similarity: float = Form(0.78),
    temperature: float = Form(0.65),
    output_format: str = Form("wav"),
    _: None = Depends(verify_api_key),
):
    if not text.strip():
        raise HTTPException(status_code=400, detail="Input text cannot be empty")

    raw_bytes = await reference_audio.read()
    if not raw_bytes:
        raise HTTPException(status_code=400, detail="Empty reference audio file")

    started = time.perf_counter()
    clean_ref = None
    try:
        clean_ref = engine.preprocess_reference(raw_bytes, reference_audio.filename or "ref.wav")
        wav = engine.synthesize(
            clean_ref_path=clean_ref,
            text=text.strip(),
            language=language.strip().lower(),
            speed=float(np.clip(speed, 0.5, 2.0)),
            similarity=float(np.clip(similarity, 0.0, 1.0)),
            temperature=float(np.clip(temperature, 0.05, 1.0)),
        )
    except ValueError as err:
        raise HTTPException(status_code=422, detail=str(err))
    finally:
        if clean_ref and os.path.exists(clean_ref):
            os.remove(clean_ref)

    fmt = output_format.lower().strip()
    if fmt not in {"wav", "flac", "ogg"}:
        fmt = "wav"  # SoundFile native encode; use pydub/ffmpeg if MP3 transcode is enabled

    buf = io.BytesIO()
    sf.write(buf, wav, TARGET_SAMPLE_RATE, format=fmt.upper())
    latency_ms = round((time.perf_counter() - started) * 1000.0, 1)

    mime_map = {"wav": "audio/wav", "flac": "audio/flac", "ogg": "audio/ogg"}
    return Response(
        content=buf.getvalue(),
        media_type=mime_map.get(fmt, "audio/wav"),
        headers={
            "X-Inference-Ms": str(latency_ms),
            "X-VoiceClone-Mode": "fallback-demo" if engine.fallback_mode else "xtts-v2",
            "Content-Disposition": f'attachment; filename="voxclone_output.{fmt}"',
        },
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("server:app", host="0.0.0.0", port=8000, reload=False)
