package com.mnemo.chat;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.Toast;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads JSON / PDF / images (OCR) / ZIP / text files and splits them into searchable chunks. */
public class Ingest {

    public static void importUris(Activity a, List<Uri> uris, long projectId, Runnable done) {
        Toast.makeText(a, "Importing " + uris.size() + " file(s)…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int ok = 0;
            StringBuilder err = new StringBuilder();
            for (Uri u : uris) {
                String name = "file";
                try {
                    name = displayName(a, u);
                    String text = read(a, u, name);
                    List<String> ch = chunk(text);
                    if (ch.isEmpty()) throw new Exception("no readable text");
                    Db.get(a).addDoc(name, type(name), projectId, ch);
                    ok++;
                } catch (Throwable t) {
                    err.append("\n").append(name).append(": ").append(t.getMessage());
                }
            }
            final int n = ok;
            final String e = err.toString();
            a.runOnUiThread(() -> {
                Toast.makeText(a, n + " added" + e, Toast.LENGTH_LONG).show();
                if (done != null) done.run();
            });
        }).start();
    }

    static String displayName(Context c, Uri u) {
        String n = null;
        try (Cursor cur = c.getContentResolver().query(u, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) n = cur.getString(i);
            }
        } catch (Exception ignored) { }
        if (n == null) n = u.getLastPathSegment();
        return n == null ? "file" : n;
    }

    static String type(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".pdf")) return "pdf";
        if (isImage(n)) return "image";
        if (n.endsWith(".zip")) return "zip";
        if (n.endsWith(".json")) return "json";
        return "text";
    }

    static boolean isImage(String n) {
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
    }

    static String read(Context c, Uri u, String name) throws Exception {
        String n = name.toLowerCase();
        if (n.endsWith(".pdf")) return pdf(c, u);
        if (isImage(n)) return ocr(c, u);
        if (n.endsWith(".zip")) return zip(c, u);
        String s;
        try (InputStream in = c.getContentResolver().openInputStream(u)) { s = readAll(in); }
        return n.endsWith(".json") ? json(s) : s;
    }

    static String pdf(Context c, Uri u) throws Exception {
        PDFBoxResourceLoader.init(c.getApplicationContext());
        try (InputStream in = c.getContentResolver().openInputStream(u); PDDocument d = PDDocument.load(in)) {
            return new PDFTextStripper().getText(d);
        }
    }

    static String ocr(Context c, Uri u) throws Exception {
        InputImage img = InputImage.fromFilePath(c, u);
        TextRecognizer r = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Text t = Tasks.await(r.process(img));
        return t.getText();
    }

    static String zip(Context c, Uri u) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream z = new ZipInputStream(c.getContentResolver().openInputStream(u))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String n = e.getName().toLowerCase();
                if (!(n.endsWith(".json") || n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".csv"))) continue;
                String s = readAll(z);
                sb.append("# ").append(e.getName()).append("\n\n").append(n.endsWith(".json") ? json(s) : s).append("\n\n");
            }
        }
        return sb.toString();
    }

    static String json(String s) {
        try {
            Object v = new JSONTokener(s).nextValue();
            StringBuilder sb = new StringBuilder();
            flat(v, "", sb);
            return sb.toString();
        } catch (Exception e) {
            return s;
        }
    }

    static void flat(Object o, String key, StringBuilder sb) {
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < a.length(); i++) {
                Object x = a.opt(i);
                flat(x, key, sb);
                if (x instanceof JSONObject) sb.append("\n\n");
            }
        } else if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            Iterator<String> it = j.keys();
            while (it.hasNext()) {
                String k = it.next();
                Object v = j.opt(k);
                if (v instanceof JSONObject || v instanceof JSONArray) flat(v, k, sb);
                else sb.append(k).append(": ").append(v).append("\n");
            }
        } else if (o != null && !JSONObject.NULL.equals(o)) {
            sb.append(key.isEmpty() ? "" : key + ": ").append(o).append("\n");
        }
    }

    /** Reads a stream fully WITHOUT closing it (so it works for zip entries). */
    public static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        return bo.toString("UTF-8");
    }

    public static List<String> chunk(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        List<String> parts = new ArrayList<>();
        for (String p : text.replace("\r", "").split("\\n\\s*\\n")) {
            p = p.trim();
            while (p.length() > 900) {
                int cut = p.lastIndexOf(". ", 800);
                if (cut < 300) cut = p.lastIndexOf(' ', 800);
                if (cut < 300) cut = 800;
                parts.add(p.substring(0, cut + 1).trim());
                p = p.substring(cut + 1).trim();
            }
            if (!p.isEmpty()) parts.add(p);
        }
        StringBuilder cur = new StringBuilder();
        for (String part : parts) {
            if (cur.length() > 0 && cur.length() + part.length() > 700) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            }
            cur.append(part).append("\n\n");
        }
        if (cur.toString().trim().length() > 0) out.add(cur.toString().trim());
        return out;
    }
}
