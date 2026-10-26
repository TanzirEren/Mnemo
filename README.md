# Mnemo

Offline chat app that answers from the data you add (JSON, PDF, PNG/JPG with OCR, ZIP, TXT/MD/CSV).
Package: `com.mnemo.chat` · Java + XML · minSdk 24

## Build on GitHub Actions
1. Create a new GitHub repo and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab. The build starts on push (or run "Build Mnemo APK" manually).
3. When it finishes, open the run and download the **Mnemo-debug-apk** artifact. Unzip and install `app-debug.apk`.

## Optional: real chat-style AI (fully offline)
Settings > "Import AI model file". Use a MediaPipe-compatible LLM `.task` file
(for example Gemma 2B / Gemma 3 1B int4 from Kaggle or Hugging Face litert-community).
Phone with 4 GB+ RAM recommended. Without a model, Mnemo answers by searching your data.

## tools/prepare_data.py
Python helper that bundles a folder of notes into a single JSON file you can import.
