package com.mnemo.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablesPlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    DrawerLayout drawer;
    RecyclerView list;
    EditText input;
    View empty;
    TextView title, scope, modes;
    LinearLayout dProjects, dData, dChats, dFoot;
    ImageButton btnSend;

    final List<Db.Msg> msgs = new ArrayList<>();
    MsgAdapter adapter;
    Db db;
    Markwon markwon;
    int accent;

    long chatId = 0, projectId = 0;
    boolean webOn = false, ultraOn = false, busy = false;

    final ExecutorService ex = Executors.newSingleThreadExecutor();
    final Handler ui = new Handler(Looper.getMainLooper());
    ActivityResultLauncher<String[]> picker;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        db = Db.get(this);

        drawer = findViewById(R.id.drawer);
        list = findViewById(R.id.list);
        input = findViewById(R.id.input);
        empty = findViewById(R.id.empty);
        title = findViewById(R.id.title);
        scope = findViewById(R.id.scope);
        modes = findViewById(R.id.modes);
        dProjects = findViewById(R.id.dProjects);
        dData = findViewById(R.id.dData);
        dChats = findViewById(R.id.dChats);
        dFoot = findViewById(R.id.dFoot);
        btnSend = findViewById(R.id.btnSend);

        picker = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (uris != null && !uris.isEmpty()) Ingest.importUris(this, uris, projectId, this::refreshDrawer);
        });

        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MsgAdapter();
        list.setAdapter(adapter);

        drawer.setScrimColor(0x22000000);
        findViewById(R.id.btnMenu).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        findViewById(R.id.btnNew).setOnClickListener(v -> newChat());
        findViewById(R.id.btnMore).setOnClickListener(this::showMore);
        findViewById(R.id.btnPlus).setOnClickListener(v -> showPlus());
        btnSend.setOnClickListener(v -> send(input.getText().toString()));

        int[] chips = {R.id.chip1, R.id.chip2, R.id.chip3};
        for (int id : chips) {
            findViewById(id).setOnClickListener(v -> {
                input.setText(((TextView) v).getText());
                input.setSelection(input.getText().length());
            });
        }

        addRow(dData, R.drawable.ic_data, "Add data", false,
                v -> { drawer.closeDrawers(); startActivity(new Intent(this, DataActivity.class).putExtra("pid", projectId)); }, null);
        addRow(dFoot, R.drawable.ic_settings, "Settings", false,
                v -> { drawer.closeDrawers(); startActivity(new Intent(this, SettingsActivity.class)); }, null);

        updateEmpty();
    }

    @Override
    protected void onResume() {
        super.onResume();
        accent = Prefs.accent(this);
        markwon = Markwon.builder(this)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablesPlugin.create(this))
                .usePlugin(TaskListPlugin.create(this))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder.linkColor(accent).isLinkUnderlined(true).blockQuoteColor(accent)
                                .codeBackgroundColor(0xFFF1F1F1).codeBlockBackgroundColor(0xFFF5F5F5);
                    }
                })
                .build();
        btnSend.setBackgroundTintList(ColorStateList.valueOf(accent));
        adapter.notifyDataSetChanged();
        updateScope();
        refreshDrawer();
    }

    // ------------------------------------------------------------ drawer
    View addRow(LinearLayout parent, int icon, String text, boolean selected, View.OnClickListener c, View.OnLongClickListener lc) {
        View r = getLayoutInflater().inflate(R.layout.item_row, parent, false);
        ((ImageView) r.findViewById(R.id.ic)).setImageResource(icon);
        TextView t = r.findViewById(R.id.txt);
        t.setText(text);
        if (selected) {
            r.setBackgroundResource(R.drawable.bg_row_sel);
            t.setTypeface(t.getTypeface(), Typeface.BOLD);
        }
        r.setOnClickListener(c);
        if (lc != null) r.setOnLongClickListener(lc);
        parent.addView(r);
        return r;
    }

    void refreshDrawer() {
        dProjects.removeAllViews();
        dChats.removeAllViews();
        for (Db.Project p : db.projects()) {
            addRow(dProjects, R.drawable.ic_folder, p.name, p.id == projectId,
                    v -> { projectId = (projectId == p.id) ? 0 : p.id; updateScope(); refreshDrawer(); },
                    v -> {
                        Ui.confirm(this, "Delete project \"" + p.name + "\" and all its data?", () -> {
                            db.deleteProject(p.id);
                            if (projectId == p.id) projectId = 0;
                            updateScope();
                            refreshDrawer();
                        });
                        return true;
                    });
        }
        addRow(dProjects, R.drawable.ic_plus, "New project", false,
                v -> Ui.prompt(this, "New project", "", "Create", s -> { projectId = db.addProject(s); updateScope(); refreshDrawer(); }), null);

        for (Db.Chat c : db.chats()) {
            addRow(dChats, R.drawable.ic_chat, c.title, c.id == chatId, v -> openChat(c.id), v -> { chatMenu(c.id); return true; });
        }
    }

    void chatMenu(long id) {
        new AlertDialog.Builder(this).setItems(new String[]{"Rename", "Delete"}, (d, w) -> {
            if (w == 0) {
                Ui.prompt(this, "Rename chat", db.chatTitle(id), "Save", s -> {
                    db.renameChat(id, s);
                    if (id == chatId) title.setText(s);
                    refreshDrawer();
                });
            } else {
                Ui.confirm(this, "Delete this chat?", () -> {
                    db.deleteChat(id);
                    if (id == chatId) newChat();
                    refreshDrawer();
                });
            }
        }).show();
    }

    void updateScope() {
        scope.setText(projectId == 0 ? "All data" : "Project: " + db.projectName(projectId));
    }

    void updateEmpty() { empty.setVisibility(msgs.isEmpty() ? View.VISIBLE : View.GONE); }

    void updateModes() {
        List<String> m = new ArrayList<>();
        if (webOn) m.add("Web search on");
        if (ultraOn) m.add("Ultra thinking on");
        modes.setText(String.join("  •  ", m));
        modes.setVisibility(m.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------ chats
    void newChat() {
        chatId = 0;
        msgs.clear();
        adapter.notifyDataSetChanged();
        title.setText("Mnemo");
        updateEmpty();
        drawer.closeDrawers();
        refreshDrawer();
    }

    void openChat(long id) {
        chatId = id;
        projectId = db.chatProject(id);
        msgs.clear();
        msgs.addAll(db.msgs(id));
        adapter.notifyDataSetChanged();
        title.setText(db.chatTitle(id));
        updateScope();
        updateEmpty();
        if (!msgs.isEmpty()) list.scrollToPosition(msgs.size() - 1);
        drawer.closeDrawers();
        refreshDrawer();
    }

    void addToList(Db.Msg m) {
        msgs.add(m);
        adapter.notifyItemInserted(msgs.size() - 1);
        list.scrollToPosition(msgs.size() - 1);
        updateEmpty();
    }

    void send(String raw) {
        String q = raw.trim();
        if (q.isEmpty()) return;
        if (busy) { Toast.makeText(this, "Mnemo is still thinking…", Toast.LENGTH_SHORT).show(); return; }
        if (chatId == 0) {
            chatId = db.addChat(q.length() > 34 ? q.substring(0, 34) + "…" : q, projectId);
            title.setText(db.chatTitle(chatId));
        }
        addToList(db.addMsg(chatId, "user", q));
        input.setText("");
        if (Prefs.autoLearn(this) && !q.endsWith("?") && q.length() >= 25) db.learn(projectId, q);
        refreshDrawer();
        runAnswer(q);
    }

    void runAnswer(String q) {
        busy = true;
        List<String> hist = new ArrayList<>();
        int from = Math.max(0, msgs.size() - 5);
        for (int i = from; i < msgs.size() - 1; i++) {
            Db.Msg m = msgs.get(i);
            if (m.role.equals("typing")) continue;
            String t = m.text.length() > 300 ? m.text.substring(0, 300) : m.text;
            hist.add((m.role.equals("user") ? "User: " : "Mnemo: ") + t.replace("\n", " "));
        }
        Db.Msg typing = new Db.Msg();
        typing.role = "typing";
        addToList(typing);

        final boolean web = webOn, ultra = ultraOn;
        final long pid = projectId, cid = chatId;
        ex.execute(() -> {
            String ans;
            try {
                ans = Answer.make(this, q, pid, web, ultra, hist);
            } catch (Throwable t) {
                ans = "Something went wrong: " + t.getMessage();
            }
            final String a = ans;
            ui.post(() -> {
                busy = false;
                Db.Msg m = db.addMsg(cid, "ai", a);
                int idx = msgs.indexOf(typing);
                if (idx >= 0) { msgs.remove(idx); adapter.notifyItemRemoved(idx); }
                if (cid == chatId) addToList(m);
            });
        });
    }

    void regen(Db.Msg m) {
        if (busy) return;
        int i = msgs.indexOf(m);
        String q = null;
        for (int j = i - 1; j >= 0; j--) {
            if (msgs.get(j).role.equals("user")) { q = msgs.get(j).text; break; }
        }
        if (q == null) return;
        db.deleteMsg(m.id);
        msgs.remove(i);
        adapter.notifyItemRemoved(i);
        runAnswer(q);
    }

    // ------------------------------------------------------------ menus
    void showMore(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "Rename chat");
        pm.getMenu().add(0, 2, 1, "Export chat");
        pm.getMenu().add(0, 3, 2, "Delete chat");
        pm.getMenu().add(0, 4, 3, "Settings");
        pm.setOnMenuItemClickListener(it -> {
            switch (it.getItemId()) {
                case 1:
                    if (chatId != 0) chatMenuRename(); else Toast.makeText(this, "Start a chat first", Toast.LENGTH_SHORT).show();
                    break;
                case 2: exportChat(); break;
                case 3:
                    if (chatId != 0) Ui.confirm(this, "Delete this chat?", () -> { db.deleteChat(chatId); newChat(); });
                    break;
                case 4: startActivity(new Intent(this, SettingsActivity.class)); break;
            }
            return true;
        });
        pm.show();
    }

    void chatMenuRename() {
        Ui.prompt(this, "Rename chat", db.chatTitle(chatId), "Save", s -> { db.renameChat(chatId, s); title.setText(s); refreshDrawer(); });
    }

    void exportChat() {
        if (msgs.isEmpty()) { Toast.makeText(this, "Nothing to export", Toast.LENGTH_SHORT).show(); return; }
        StringBuilder sb = new StringBuilder("Mnemo chat: " + title.getText() + "\n\n");
        for (Db.Msg m : msgs) {
            if (m.role.equals("typing")) continue;
            sb.append(m.role.equals("user") ? "You: " : "Mnemo: ").append(m.text).append("\n\n");
        }
        share(sb.toString());
    }

    void share(String text) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "Share"));
    }

    void showPlus() {
        BottomSheetDialog bs = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.sheet_plus, null);
        SwitchCompat sw = v.findViewById(R.id.swWeb), su = v.findViewById(R.id.swUltra);
        sw.setChecked(webOn);
        su.setChecked(ultraOn);
        sw.setOnCheckedChangeListener((x, c) -> { webOn = c; updateModes(); });
        su.setOnCheckedChangeListener((x, c) -> { ultraOn = c; updateModes(); });
        v.findViewById(R.id.rowWeb).setOnClickListener(x -> sw.toggle());
        v.findViewById(R.id.rowUltra).setOnClickListener(x -> su.toggle());
        v.findViewById(R.id.rowFile).setOnClickListener(x -> { bs.dismiss(); picker.launch(new String[]{"*/*"}); });
        v.findViewById(R.id.rowNote).setOnClickListener(x -> {
            bs.dismiss();
            Ui.prompt(this, "Add a note to memory", "", "Save", s -> {
                String name = "Note: " + (s.length() > 24 ? s.substring(0, 24) + "…" : s);
                db.addDoc(name, "note", projectId, Ingest.chunk(s));
                Toast.makeText(this, "Saved to memory", Toast.LENGTH_SHORT).show();
            });
        });
        bs.setContentView(v);
        bs.show();
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawers();
        else super.onBackPressed();
    }

    // ------------------------------------------------------------ adapter
    class MsgAdapter extends RecyclerView.Adapter<MsgAdapter.VH> {

        @Override public int getItemViewType(int p) { return msgs.get(p).role.equals("user") ? 0 : 1; }
        @Override public int getItemCount() { return msgs.size(); }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View v = LayoutInflater.from(parent.getContext()).inflate(type == 0 ? R.layout.item_user : R.layout.item_ai, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Db.Msg m = msgs.get(pos);
            if (m.role.equals("user")) {
                h.t.setText(m.text);
                h.t.getBackground().mutate().setTint(Ui.lighten(accent));
                h.t.setOnLongClickListener(v -> { copy(m.text); return true; });
                return;
            }
            if (m.role.equals("typing")) {
                h.t.setText("Mnemo is thinking…");
                h.t.setTextColor(0x99000000);
                h.actions.setVisibility(View.GONE);
                return;
            }
            h.t.setTextColor(0xFF111111);
            h.actions.setVisibility(View.VISIBLE);
            markwon.setMarkdown(h.t, m.text);
            String q = "";
            for (int j = pos - 1; j >= 0; j--) {
                if (msgs.get(j).role.equals("user")) { q = msgs.get(j).text; break; }
            }
            highlight(h.t, q);
            h.t.setOnLongClickListener(v -> { copy(m.text); return true; });
            h.copy.setOnClickListener(v -> copy(m.text));
            h.share.setOnClickListener(v -> share(m.text));
            h.regen.setOnClickListener(v -> regen(m));
        }

        class VH extends RecyclerView.ViewHolder {
            TextView t;
            View actions;
            ImageButton copy, share, regen;

            VH(View v) {
                super(v);
                t = v.findViewById(R.id.msg);
                actions = v.findViewById(R.id.actions);
                copy = v.findViewById(R.id.aCopy);
                share = v.findViewById(R.id.aShare);
                regen = v.findViewById(R.id.aRegen);
            }
        }
    }

    void highlight(TextView tv, String q) {
        CharSequence cs = tv.getText();
        if (!(cs instanceof Spannable)) return;
        Spannable sp = (Spannable) cs;
        String low = cs.toString().toLowerCase();
        for (String t : Bm25.tokens(q)) {
            if (t.length() < 3) continue;
            int i = 0;
            while ((i = low.indexOf(t, i)) >= 0) {
                boolean start = i == 0 || !Character.isLetterOrDigit(low.charAt(i - 1));
                if (start && i + t.length() <= sp.length()) {
                    sp.setSpan(new BackgroundColorSpan(0x66FFEB3B), i, i + t.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                i += t.length();
            }
        }
    }

    void copy(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Mnemo", s));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }
}
