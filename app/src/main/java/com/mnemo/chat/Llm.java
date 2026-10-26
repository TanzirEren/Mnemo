package com.mnemo.chat;

import android.content.Context;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import java.io.File;

/** Optional on-device language model (MediaPipe LLM Inference). Works only after a model file is imported. */
public class Llm {
    private static LlmInference engine;

    public static File modelFile(Context c) { return new File(c.getFilesDir(), "model.task"); }

    public static boolean available(Context c) {
        File f = modelFile(c);
        return f.exists() && f.length() > 1000;
    }

    public static synchronized String generate(Context c, String prompt) {
        if (engine == null) {
            LlmInference.LlmInferenceOptions o = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile(c).getAbsolutePath())
                    .setMaxTokens(1280)
                    .build();
            engine = LlmInference.createFromOptions(c.getApplicationContext(), o);
        }
        return engine.generateResponse(prompt);
    }

    public static synchronized void reset() {
        if (engine != null) {
            try { engine.close(); } catch (Throwable ignored) { }
            engine = null;
        }
    }
}
