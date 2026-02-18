package com.microapp.analysis.model;

import java.util.List;

public record AnalysisSummary(
    String accountId,
    String statementId,
    double totalSpend,
    List<CategoryTotal> categories,
    List<MerchantTotal> topMerchants,
    List<Insight> insights
) {}
