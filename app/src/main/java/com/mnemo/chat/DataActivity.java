package com.mnemo.chat;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class DataActivity extends AppCompatActivity {
    long pid;
    Db db;
    LinearLayout docList;
    TextView emptyText;
    ActivityResultLauncher<String[]> picker;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_data);
        db = Db.get(this);
        pid = getIntent().getLongExtra("pid", 0);
        docList = findViewById(R.id.docList);
        emptyText = findViewById(R.id.emptyText);
        ((TextView) findViewById(R.id.scope)).setText(pid == 0 ? "Adding to: All data" : "Adding to: " + db.projectName(pid));
        findViewById(R.id.back).setOnClickListener(v -> finish());

        picker = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (uris != null && !uris.isEmpty()) Ingest.importUris(this, uris, pid, this::refresh);
        });
        findViewById(R.id.btnFiles).setOnClickListener(v -> picker.launch(new String[]{"*/*"}));
        findViewById(R.id.btnNote).setOnClickListener(v ->
                Ui.prompt(this, "Write a note", "", "Save", s -> {
                    String name = "Note: " + (s.length() > 24 ? s.substring(0, 24) + "…" : s);
                    db.addDoc(name, "note", pid, Ingest.chunk(s));
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                    refresh();
                }));
        refresh();
    }

    void refresh() {
        docList.removeAllViews();
        List<Db.Doc> docs = db.docs(pid);
        emptyText.setVisibility(docs.isEmpty() ? View.VISIBLE : View.GONE);
        for (Db.Doc d : docs) {
            View r = getLayoutInflater().inflate(R.layout.item_doc, docList, false);
            ((TextView) r.findViewById(R.id.name)).setText(d.name);
            ((TextView) r.findViewById(R.id.meta)).setText(d.type.toUpperCase() + "  ·  " + d.chunks + " passages");
            r.findViewById(R.id.del).setOnClickListener(v ->
                    Ui.confirm(this, "Delete \"" + d.name + "\"?", () -> { db.deleteDoc(d.id); refresh(); }));
            docList.addView(r);
        }
    }
}
