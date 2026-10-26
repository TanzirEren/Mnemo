package com.mnemo.chat;

import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Optional web lookup (Wikipedia). Only used when the user switches Web search on. */
public class WebSearch {
    /** Each result: {title, extract, url, index}. */
    public static List<String[]> search(String q) throws Exception {
        String u = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch="
                + URLEncoder.encode(q, "UTF-8")
                + "&gsrlimit=3&prop=extracts%7Cinfo&exintro=1&explaintext=1&exlimit=3&inprop=url&format=json";
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "Mnemo/1.0 (Android)");
        String s = Ingest.readAll(c.getInputStream());
        JSONObject root = new JSONObject(s);
        List<String[]> out = new ArrayList<>();
        if (!root.has("query")) return out;
        JSONObject pages = root.getJSONObject("query").getJSONObject("pages");
        Iterator<String> it = pages.keys();
        while (it.hasNext()) {
            JSONObject p = pages.getJSONObject(it.next());
            out.add(new String[]{p.optString("title"), p.optString("extract"), p.optString("fullurl"), "" + p.optInt("index", 99)});
        }
        Collections.sort(out, (a, b) -> Integer.compare(Integer.parseInt(a[3]), Integer.parseInt(b[3])));
        return out;
    }
}
