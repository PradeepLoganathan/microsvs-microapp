package com.microapp.analysis.store;

import com.microapp.analysis.model.AnalysisSummary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AnalysisStore {

    private static final Map<String, AnalysisSummary> STORE = new ConcurrentHashMap<>();

    private static String key(String accountId, String statementId) {
        return accountId + ":" + statementId;
    }

    public static void save(String accountId, String statementId, AnalysisSummary summary) {
        STORE.put(key(accountId, statementId), summary);
    }

    public static AnalysisSummary get(String accountId, String statementId) {
        return STORE.get(key(accountId, statementId));
    }
}
