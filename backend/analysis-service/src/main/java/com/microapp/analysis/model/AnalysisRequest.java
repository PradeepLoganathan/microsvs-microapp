package com.microapp.analysis.model;

public record AnalysisRequest(
    String accountId,
    String statementId,
    String sessionId
) {}
