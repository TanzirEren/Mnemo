package com.mnemo.chat;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    static SharedPreferences p(Context c) { return c.getSharedPreferences("mnemo", Context.MODE_PRIVATE); }
    public static int accent(Context c) { return p(c).getInt("accent", 0xFF7C4DFF); }
    public static void setAccent(Context c, int v) { p(c).edit().putInt("accent", v).apply(); }
    public static boolean autoLearn(Context c) { return p(c).getBoolean("learn", true); }
    public static void setAutoLearn(Context c, boolean v) { p(c).edit().putBoolean("learn", v).apply(); }
    public static boolean useLlm(Context c) { return p(c).getBoolean("llm", true); }
    public static void setUseLlm(Context c, boolean v) { p(c).edit().putBoolean("llm", v).apply(); }
}
