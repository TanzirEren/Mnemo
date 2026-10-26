package com.mnemo.chat;

import android.content.Context;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds Mnemo's reply: retrieval from local data (+ optional web + optional on-device LLM). */
public class Answer {

    public static String make(Context c, String q, long projectId, boolean web, boolean ultra, List<String> history) throws Exception {
        if (q.toLowerCase().trim().matches("^(hi|hello|hey|hola|salam|assalamu alaikum|assalamualaikum)[ !.]*$")) {
            return "Hello! I'm **Mnemo**. Ask me anything about the data you've added, "
                    + "or add more from the **+** button.";
        }

        Db db = Db.get(c);
        List<Db.Chunk> all = db.chunks(projectId);
        List<Bm25.Hit> hits = Bm25.search(q, all, ultra ? 8 : 4);

        List<String[]> webRes = new ArrayList<>();
        if (web) {
            try { webRes = WebSearch.search(q); } catch (Exception e) { /* offline: ignore */ }
        }

        if (Llm.available(c) && Prefs.useLlm(c)) {
            StringBuilder ctx = new StringBuilder();
            int i = 1;
            for (Bm25.Hit h : hits) {
                if (ctx.length() > 2800) break;
                ctx.append("[").append(i++).append("] (").append(h.chunk.docName).append(") ").append(h.chunk.text).append("\n");
            }
            for (String[] w : webRes) {
                if (ctx.length() > 3600) break;
                ctx.append("[web] (").append(w[0]).append(") ").append(cut(w[1], 600)).append("\n");
            }
            StringBuilder hist = new StringBuilder();
            for (String h : history) hist.append(h).append("\n");
            String prompt = "<start_of_turn>user\nYou are Mnemo, a friendly assistant. Answer using the CONTEXT when it is relevant; "
                    + "if the context does not contain the answer, say so honestly. Format the answer in Markdown (bold, lists, quotes). "
                    + (ultra ? "Think step by step and give a thorough answer. " : "Be concise. ")
                    + "\n\nCONVERSATION SO FAR:\n" + hist
                    + "\nCONTEXT:\n" + (ctx.length() == 0 ? "(none)" : ctx)
                    + "\nQUESTION: " + q + "<end_of_turn>\n<start_of_turn>model\n";
            String out = Llm.generate(c, prompt).trim();
            return out + sources(hits, webRes);
        }

        if (hits.isEmpty() && webRes.isEmpty()) {
            return "I couldn't find anything about **" + clean(q) + "** in your data yet.\n\n"
                    + "> Add notes, PDFs, images or JSON from **Add data**, or switch on **Web search** in the **+** menu.";
        }

        StringBuilder sb = new StringBuilder();
        if (!hits.isEmpty()) {
            sb.append("### Best match\n");
            for (String line : hits.get(0).chunk.text.split("\n")) sb.append("> ").append(line).append("\n");
            sb.append("\n*from* **").append(hits.get(0).chunk.docName).append("**\n\n");

            if (ultra) {
                Set<Long> docs = new LinkedHashSet<>();
                for (Db.Chunk ch : all) docs.add(ch.docId);
                sb.append("### Reasoning\n");
                sb.append("1. Key terms in your question: ").append(terms(q)).append("\n");
                sb.append("2. Scanned **").append(all.size()).append("** passages across **").append(docs.size()).append("** documents\n");
                sb.append("3. Ranked with BM25 and typo-tolerant matching, top score **").append(String.format("%.2f", hits.get(0).score)).append("**\n");
                sb.append("4. Cross-checked **").append(hits.size() - 1).append("** more related passages\n\n");
            }
            if (hits.size() > 1) {
                sb.append(ultra ? "### Related passages\n" : "### Also related\n");
                int max = ultra ? hits.size() : Math.min(3, hits.size());
                for (int i = 1; i < max; i++) {
                    sb.append("- **").append(hits.get(i).chunk.docName).append("** — ").append(cut(hits.get(i).chunk.text.replace("\n", " "), ultra ? 260 : 160)).append("\n");
                }
                sb.append("\n");
            }
        }
        if (!webRes.isEmpty()) {
            sb.append("### From the web\n");
            for (String[] w : webRes) {
                sb.append("**").append(w[0]).append("**  \n").append(cut(w[1].replace("\n", " "), 380));
                if (!w[2].isEmpty()) sb.append(" [open](").append(w[2]).append(")");
                sb.append("\n\n");
            }
        }
        sb.append(sources(hits, webRes));
        return sb.toString();
    }

    static String sources(List<Bm25.Hit> hits, List<String[]> web) {
        Set<String> s = new LinkedHashSet<>();
        for (Bm25.Hit h : hits) s.add(h.chunk.docName);
        for (String[] w : web) s.add(w[0] + " (web)");
        if (s.isEmpty()) return "";
        return "\n\n---\n*Sources: " + String.join(", ", s) + "*";
    }

    static String terms(String q) {
        List<String> t = Bm25.tokens(q);
        StringBuilder sb = new StringBuilder();
        for (String x : t) sb.append(sb.length() == 0 ? "" : ", ").append("`").append(x).append("`");
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n).trim() + "…"; }

    static String clean(String s) { return s.replaceAll("[*_`#>\\[\\]]", ""); }
}
