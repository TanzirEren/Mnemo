package com.mnemo.chat;

import android.content.Context;
import android.graphics.Color;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.appcompat.app.AlertDialog;
import java.util.function.Consumer;

public class Ui {
    public static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    /** Blend a colour 85% toward white (soft tint). */
    public static int lighten(int c) {
        return Color.rgb((Color.red(c) * 15 + 255 * 85) / 100, (Color.green(c) * 15 + 255 * 85) / 100, (Color.blue(c) * 15 + 255 * 85) / 100);
    }

    public static void prompt(Context c, String title, String initial, String ok, Consumer<String> cb) {
        EditText e = new EditText(c);
        e.setText(initial);
        e.setSingleLine(false);
        e.setMaxLines(8);
        FrameLayout f = new FrameLayout(c);
        int p = dp(c, 20);
        f.setPadding(p, dp(c, 8), p, 0);
        f.addView(e, new FrameLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(c).setTitle(title).setView(f)
                .setPositiveButton(ok, (d, w) -> {
                    String s = e.getText().toString().trim();
                    if (!s.isEmpty()) cb.accept(s);
                })
                .setNegativeButton("Cancel", null).show();
    }

    public static void confirm(Context c, String msg, Runnable r) {
        new AlertDialog.Builder(c).setMessage(msg)
                .setPositiveButton("Yes", (d, w) -> r.run())
                .setNegativeButton("Cancel", null).show();
    }
}
