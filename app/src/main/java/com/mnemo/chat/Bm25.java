package com.mnemo.chat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Typo-tolerant BM25 search, fully offline. */
public class Bm25 {
    static final double K1 = 1.4, B = 0.75;
    static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "the", "and", "for", "are", "was", "what", "who", "how", "why", "when", "where", "which", "this", "that",
            "with", "from", "you", "your", "can", "tell", "about", "please", "give", "show", "has", "have", "does",
            "did", "not", "but", "its", "his", "her", "they", "them", "there", "been", "will", "would", "could",
            "should", "ami", "amar", "tumi", "ki", "kono", "ache", "hoy", "kore", "er", "te", "ta", "na", "ke", "je"));

    public static class Hit {
        public Db.Chunk chunk;
        public double score;
    }

    public static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String t : s.toLowerCase().split("[^\\p{L}\\p{M}\\p{N}]+")) {
            if (t.length() < 2 || STOP.contains(t)) continue;
            out.add(t);
        }
        return out;
    }

    public static List<Hit> search(String q, List<Db.Chunk> chunks, int k) {
        List<String> qt = tokens(q);
        List<Hit> out = new ArrayList<>();
        int n = chunks.size();
        if (qt.isEmpty() || n == 0) return out;

        List<Map<String, Integer>> tfs = new ArrayList<>(n);
        int[] len = new int[n];
        double avg = 0;
        for (int i = 0; i < n; i++) {
            Map<String, Integer> m = new HashMap<>();
            List<String> t = tokens(chunks.get(i).text);
            for (String w : t) m.merge(w, 1, Integer::sum);
            tfs.add(m);
            len[i] = t.size();
            avg += t.size();
        }
        avg = Math.max(1, avg / n);

        double[] score = new double[n];
        for (String term : qt) {
            double[] w = new double[n];
            int df = 0;
            for (int i = 0; i < n; i++) {
                w[i] = match(term, tfs.get(i));
                if (w[i] > 0) df++;
            }
            if (df == 0) continue;
            double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
            for (int i = 0; i < n; i++) {
                if (w[i] <= 0) continue;
                double tf = w[i];
                score[i] += idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * len[i] / avg));
            }
        }
        for (int i = 0; i < n; i++) {
            if (score[i] > 0) {
                Hit h = new Hit();
                h.chunk = chunks.get(i);
                h.score = score[i];
                out.add(h);
            }
        }
        Collections.sort(out, (a, b) -> Double.compare(b.score, a.score));
        return out.size() > k ? new ArrayList<>(out.subList(0, k)) : out;
    }

    static double match(String term, Map<String, Integer> m) {
        Integer e = m.get(term);
        if (e != null) return e;
        if (term.length() < 4) return 0;
        double best = 0;
        for (Map.Entry<String, Integer> en : m.entrySet()) {
            String w = en.getKey();
            if (w.length() < 4) continue;
            if (w.startsWith(term) || term.startsWith(w)) best += 0.7 * en.getValue();
            else if (term.length() >= 5 && w.length() >= 5 && within1(w, term)) best += 0.6 * en.getValue();
        }
        return best;
    }

    static boolean within1(String a, String b) {
        int la = a.length(), lb = b.length();
        if (Math.abs(la - lb) > 1) return false;
        int i = 0, j = 0, d = 0;
        while (i < la && j < lb) {
            if (a.charAt(i) == b.charAt(j)) { i++; j++; }
            else {
                if (++d > 1) return false;
                if (la > lb) i++;
                else if (lb > la) j++;
                else { i++; j++; }
            }
        }
        return d + (la - i) + (lb - j) <= 1;
    }
}
