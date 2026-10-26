package com.mnemo.chat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class Db extends SQLiteOpenHelper {
    public static class Project { public long id; public String name; }
    public static class Chat { public long id, projectId; public String title; }
    public static class Msg { public long id; public String role = "ai", text = ""; }
    public static class Doc { public long id, projectId; public String name, type; public int chunks; }
    public static class Chunk { public long id, docId; public String text, docName; }

    private static Db inst;

    public static synchronized Db get(Context c) {
        if (inst == null) inst = new Db(c.getApplicationContext());
        return inst;
    }

    private Db(Context c) { super(c, "mnemo.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, created INTEGER)");
        d.execSQL("CREATE TABLE chats(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, project_id INTEGER DEFAULT 0, created INTEGER)");
        d.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, chat_id INTEGER, role TEXT, text TEXT, created INTEGER)");
        d.execSQL("CREATE TABLE docs(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, type TEXT, project_id INTEGER DEFAULT 0, created INTEGER)");
        d.execSQL("CREATE TABLE chunks(id INTEGER PRIMARY KEY AUTOINCREMENT, doc_id INTEGER, project_id INTEGER DEFAULT 0, text TEXT)");
        d.execSQL("CREATE INDEX idx_msg_chat ON messages(chat_id)");
        d.execSQL("CREATE INDEX idx_chunk_doc ON chunks(doc_id)");
    }

    @Override public void onUpgrade(SQLiteDatabase d, int o, int n) { }

    // ---------- projects ----------
    public List<Project> projects() {
        List<Project> l = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name FROM projects ORDER BY id", null)) {
            while (c.moveToNext()) { Project p = new Project(); p.id = c.getLong(0); p.name = c.getString(1); l.add(p); }
        }
        return l;
    }

    public long addProject(String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insert("projects", null, v);
    }

    public String projectName(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT name FROM projects WHERE id=?", new String[]{"" + id})) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }

    public void deleteProject(long id) {
        SQLiteDatabase d = getWritableDatabase();
        d.execSQL("DELETE FROM chunks WHERE project_id=" + id);
        d.execSQL("DELETE FROM docs WHERE project_id=" + id);
        d.execSQL("UPDATE chats SET project_id=0 WHERE project_id=" + id);
        d.execSQL("DELETE FROM projects WHERE id=" + id);
    }

    // ---------- chats ----------
    public List<Chat> chats() {
        List<Chat> l = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,title,project_id FROM chats ORDER BY id DESC", null)) {
            while (c.moveToNext()) { Chat x = new Chat(); x.id = c.getLong(0); x.title = c.getString(1); x.projectId = c.getLong(2); l.add(x); }
        }
        return l;
    }

    public long addChat(String title, long projectId) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("project_id", projectId);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insert("chats", null, v);
    }

    public void renameChat(long id, String t) {
        ContentValues v = new ContentValues();
        v.put("title", t);
        getWritableDatabase().update("chats", v, "id=?", new String[]{"" + id});
    }

    public String chatTitle(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT title FROM chats WHERE id=?", new String[]{"" + id})) {
            return c.moveToFirst() ? c.getString(0) : "Mnemo";
        }
    }

    public long chatProject(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT project_id FROM chats WHERE id=?", new String[]{"" + id})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public void deleteChat(long id) {
        SQLiteDatabase d = getWritableDatabase();
        d.execSQL("DELETE FROM messages WHERE chat_id=" + id);
        d.execSQL("DELETE FROM chats WHERE id=" + id);
    }

    // ---------- messages ----------
    public Msg addMsg(long chatId, String role, String text) {
        ContentValues v = new ContentValues();
        v.put("chat_id", chatId);
        v.put("role", role);
        v.put("text", text);
        v.put("created", System.currentTimeMillis());
        Msg m = new Msg();
        m.id = getWritableDatabase().insert("messages", null, v);
        m.role = role;
        m.text = text;
        return m;
    }

    public List<Msg> msgs(long chatId) {
        List<Msg> l = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,role,text FROM messages WHERE chat_id=? ORDER BY id", new String[]{"" + chatId})) {
            while (c.moveToNext()) { Msg m = new Msg(); m.id = c.getLong(0); m.role = c.getString(1); m.text = c.getString(2); l.add(m); }
        }
        return l;
    }

    public void deleteMsg(long id) { getWritableDatabase().delete("messages", "id=?", new String[]{"" + id}); }

    // ---------- documents ----------
    public long addDoc(String name, String type, long projectId, List<String> chunks) {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("name", name);
            v.put("type", type);
            v.put("project_id", projectId);
            v.put("created", System.currentTimeMillis());
            long id = d.insert("docs", null, v);
            for (String ch : chunks) {
                ContentValues c = new ContentValues();
                c.put("doc_id", id);
                c.put("project_id", projectId);
                c.put("text", ch);
                d.insert("chunks", null, c);
            }
            d.setTransactionSuccessful();
            return id;
        } finally {
            d.endTransaction();
        }
    }

    /** Auto-learn: append a statement the user typed to the "Chat memory" document. */
    public void learn(long projectId, String text) {
        SQLiteDatabase d = getWritableDatabase();
        long id = -1;
        try (Cursor c = d.rawQuery("SELECT id FROM docs WHERE name='Chat memory' AND project_id=?", new String[]{"" + projectId})) {
            if (c.moveToFirst()) id = c.getLong(0);
        }
        if (id < 0) {
            List<String> l = new ArrayList<>();
            l.add(text);
            addDoc("Chat memory", "memory", projectId, l);
        } else {
            ContentValues v = new ContentValues();
            v.put("doc_id", id);
            v.put("project_id", projectId);
            v.put("text", text);
            d.insert("chunks", null, v);
        }
    }

    public List<Doc> docs(long projectId) {
        List<Doc> l = new ArrayList<>();
        String sql = "SELECT d.id,d.name,d.type,d.project_id,(SELECT COUNT(*) FROM chunks WHERE doc_id=d.id) FROM docs d"
                + (projectId > 0 ? " WHERE d.project_id=" + projectId : "") + " ORDER BY d.id DESC";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                Doc x = new Doc();
                x.id = c.getLong(0); x.name = c.getString(1); x.type = c.getString(2); x.projectId = c.getLong(3); x.chunks = c.getInt(4);
                l.add(x);
            }
        }
        return l;
    }

    public void deleteDoc(long id) {
        SQLiteDatabase d = getWritableDatabase();
        d.execSQL("DELETE FROM chunks WHERE doc_id=" + id);
        d.execSQL("DELETE FROM docs WHERE id=" + id);
    }

    /** projectId 0 = search everything; otherwise that project plus global (project 0) data. */
    public List<Chunk> chunks(long projectId) {
        List<Chunk> l = new ArrayList<>();
        String sql = "SELECT c.id,c.doc_id,c.text,d.name FROM chunks c JOIN docs d ON d.id=c.doc_id"
                + (projectId > 0 ? " WHERE c.project_id IN (0," + projectId + ")" : "");
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                Chunk x = new Chunk();
                x.id = c.getLong(0); x.docId = c.getLong(1); x.text = c.getString(2); x.docName = c.getString(3);
                l.add(x);
            }
        }
        return l;
    }

    public int[] stats() {
        String[] t = {"projects", "chats", "docs", "chunks", "messages"};
        int[] r = new int[t.length];
        for (int i = 0; i < t.length; i++) {
            try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + t[i], null)) {
                if (c.moveToFirst()) r[i] = c.getInt(0);
            }
        }
        return r;
    }

    /** Backup in a format Mnemo can import again (JSON array of {title,type,text}). */
    public JSONArray backup() throws Exception {
        JSONArray arr = new JSONArray();
        for (Doc d : docs(0)) {
            StringBuilder sb = new StringBuilder();
            try (Cursor c = getReadableDatabase().rawQuery("SELECT text FROM chunks WHERE doc_id=? ORDER BY id", new String[]{"" + d.id})) {
                while (c.moveToNext()) sb.append(c.getString(0)).append("\n\n");
            }
            JSONObject o = new JSONObject();
            o.put("title", d.name);
            o.put("type", d.type);
            o.put("text", sb.toString().trim());
            arr.put(o);
        }
        return arr;
    }

    public void clearAll() {
        SQLiteDatabase d = getWritableDatabase();
        for (String t : new String[]{"messages", "chats", "chunks", "docs", "projects"}) d.execSQL("DELETE FROM " + t);
    }
}
