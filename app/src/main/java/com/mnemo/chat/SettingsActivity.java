package com.mnemo.chat;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class SettingsActivity extends AppCompatActivity {
    LinearLayout swatches;
    TextView llmStatus, stats;
    ActivityResultLauncher<String[]> modelPicker;
    ActivityResultLauncher<String> backupSaver;
    final int[] colors = {0xFF7C4DFF, 0xFF00B894, 0xFFFF7675, 0xFF0984E3, 0xFFFD9644, 0xFFE84393};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        swatches = findViewById(R.id.swatches);
        llmStatus = findViewById(R.id.llmStatus);
        stats = findViewById(R.id.stats);

        SwitchCompat learn = findViewById(R.id.swLearn), llm = findViewById(R.id.swLlm);
        learn.setChecked(Prefs.autoLearn(this));
        llm.setChecked(Prefs.useLlm(this));
        learn.setOnCheckedChangeListener((x, c) -> Prefs.setAutoLearn(this, c));
        llm.setOnCheckedChangeListener((x, c) -> Prefs.setUseLlm(this, c));

        modelPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) importModel(uri); });
        backupSaver = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> { if (uri != null) writeBackup(uri); });

        findViewById(R.id.btnModel).setOnClickListener(v -> modelPicker.launch(new String[]{"*/*"}));
        findViewById(R.id.btnRemoveModel).setOnClickListener(v -> {
            Llm.reset();
            Llm.modelFile(this).delete();
            refresh();
        });
        findViewById(R.id.btnBackup).setOnClickListener(v -> backupSaver.launch("mnemo-backup.json"));
        findViewById(R.id.btnClear).setOnClickListener(v ->
                Ui.confirm(this, "Erase all chats, projects and data? This cannot be undone.", () -> {
                    Db.get(this).clearAll();
                    refresh();
                }));
        buildSwatches();
        refresh();
    }

    void buildSwatches() {
        swatches.removeAllViews();
        int cur = Prefs.accent(this);
        for (int col : colors) {
            View v = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
            lp.setMarginEnd(Ui.dp(this, 12));
            v.setLayoutParams(lp);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col);
            if (col == cur) g.setStroke(Ui.dp(this, 3), Color.BLACK);
            v.setBackground(g);
            v.setOnClickListener(x -> { Prefs.setAccent(this, col); buildSwatches(); });
            swatches.addView(v);
        }
    }

    void refresh() {
        boolean has = Llm.available(this);
        llmStatus.setText(has
                ? "AI model installed (" + (Llm.modelFile(this).length() / (1024 * 1024)) + " MB)"
                : "No AI model yet. Mnemo answers by searching your data. Import a Gemma / Qwen .task file for chat-style answers.");
        int[] s = Db.get(this).stats();
        stats.setText("Projects: " + s[0] + "\nChats: " + s[1] + "\nDocuments: " + s[2] + "\nPassages: " + s[3] + "\nMessages: " + s[4]);
    }

    void importModel(Uri uri) {
        Toast.makeText(this, "Importing model… this can take a minute", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            String res;
            try {
                Llm.reset();
                File tmp = new File(getFilesDir(), "model.tmp");
                try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[1 << 16];
                    int r;
                    while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                }
                File dst = Llm.modelFile(this);
                if (dst.exists()) dst.delete();
                if (!tmp.renameTo(dst)) throw new Exception("could not save model");
                res = "Model imported";
            } catch (Throwable t) {
                res = "Import failed: " + t.getMessage();
            }
            final String m = res;
            runOnUiThread(() -> { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); refresh(); });
        }).start();
    }

    void writeBackup(Uri uri) {
        try (OutputStream o = getContentResolver().openOutputStream(uri)) {
            o.write(Db.get(this).backup().toString(2).getBytes("UTF-8"));
            Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
