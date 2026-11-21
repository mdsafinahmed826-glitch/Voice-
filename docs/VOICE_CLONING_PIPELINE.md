# Zero-Shot Voice Cloning Pipeline Guide (Python & PyTorch)

## 1. Audio Preprocessing Pipeline
Clean reference audio (5–15 seconds) determines speaker fidelity more than any hyperparameter.

```python
import numpy as np
import soundfile as sf
import librosa
import noisereduce as nr
import torch

def preprocess_reference_audio(
    input_path: str,
    output_path: str,
    target_sr: int = 24000,
    target_dbfs: float = -18.0,
) -> str:
    # 1. Load & resample to mono 24 kHz
    wav, _ = librosa.load(input_path, sr=target_sr, mono=True)

    # 2. Voice Activity Detection (Silero VAD) to keep clean speech segments
    vad_model, utils = torch.hub.load("snakers4/silero-vad", "silero_vad", trust_repo=True)
    get_speech_timestamps = utils[0]
    wav_16k = librosa.resample(wav, orig_sr=target_sr, target_sr=16000)
    speech_ts = get_speech_timestamps(torch.from_numpy(wav_16k), vad_model, sampling_rate=16000)

    if speech_ts:
        ratio = target_sr / 16000.0
        voiced_chunks = [wav[int(seg["start"] * ratio):int(seg["end"] * ratio)] for seg in speech_ts]
        wav = np.concatenate(voiced_chunks)

    # 3. Spectral stationary noise reduction
    wav = nr.reduce_noise(y=wav, sr=target_sr, prop_decrease=0.75)

    # 4. RMS loudness normalization + soft peak ceiling
    rms = np.sqrt(np.mean(wav ** 2) + 1e-9)
    wav = wav * ((10 ** (target_dbfs / 20.0)) / rms)
    peak = np.max(np.abs(wav))
    if peak > 0.95:
        wav = wav * (0.95 / peak)

    sf.write(output_path, wav.astype(np.float32), target_sr, subtype="PCM_16")
    return output_path
```

## 2. Speaker Embedding Extraction vs. End-to-End Voice Cloning
- **Two-Stage Speaker Embedding Pipelines (e.g., YourTTS / VITS + ECAPA-TDNN)**:
  - Extracts a fixed-size 192-D or 256-D d-vector from a speaker encoder (`speechbrain/spkrec-ecapa-voxceleb`).
  - Fast conditioning and tiny cache footprint, but can smooth out micro-prosody, breathiness, and accent cadence.
- **End-to-End Zero-Shot Models (XTTS-v2, F5-TTS, Fish Speech)**:
  - Condition directly on acoustic tokens / mel-spectrogram latent prefixes from the reference audio via cross-attention or flow-matching in-context learning.
  - Preserves timber, pacing, and multilingual accent transfer dramatically better with 6–10s of reference audio.

## 3. PyTorch Inference Script (CUDA / CPU + Memory Optimization)
```python
import torch
from TTS.api import TTS

device = "cuda" if torch.cuda.is_available() else "cpu"
tts = TTS("tts_models/multilingual/multi-dataset/xtts_v2").to(device)

# Cache speaker conditioning latents once per reference speaker!
model = tts.synthesizer.tts_model
gpt_cond_latent, speaker_embedding = model.get_conditioning_latents(
    audio_path=["clean_ref_24k.wav"]
)

with torch.inference_mode():
    if device == "cuda":
        with torch.amp.autocast("cuda", dtype=torch.float16):
            out = model.inference(
                text="Zero-shot cloning preserves prosody and speaker identity.",
                language="en",
                gpt_cond_latent=gpt_cond_latent,
                speaker_embedding=speaker_embedding,
                temperature=0.65,
                speed=1.0,
            )
        torch.cuda.empty_cache()
    else:
        out = model.inference(
            text="Zero-shot cloning preserves prosody and speaker identity.",
            language="en",
            gpt_cond_latent=gpt_cond_latent,
            speaker_embedding=speaker_embedding,
            temperature=0.65,
            speed=1.0,
        )
```

## 4. Production Evaluation Checklist
1. **Naturalness**:
   - Measure **UTMOS** (target >= 3.8 / 5.0) and **WER** via Whisper-large-v3 round-trip transcription (target < 4%).
   - Listen for breathing artifacts, metallic vocoder buzz, or clipped tail phonemes.
2. **Speaker Similarity**:
   - Compute cosine similarity between `ECAPA-TDNN` embeddings of `clean_ref` and synthesized audio (target >= 0.75).
3. **Latency & Throughput**:
   - Track **Real-Time Factor (RTF)** = `inference_seconds / audio_seconds` (target < 0.35 on L4/A10G GPU).
   - Track **TTFA (Time to First Audio)** under streaming mode (target < 450 ms) and monitor VRAM peak.

## 5. Setup & Running Locally / Cloud GPU
```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn server:app --host 0.0.0.0 --port 8000
```
- On Android Emulator, point Settings Endpoint URL to `http://10.0.2.2:8000`.
- On Cloud GPU (RunPod / Modal / Lambda), expose port 8000 behind HTTPS and set `VOXCLONE_API_KEY`.
