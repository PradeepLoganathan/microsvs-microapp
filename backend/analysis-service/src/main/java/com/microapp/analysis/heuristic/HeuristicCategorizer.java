package com.microapp.analysis.heuristic;

import com.microapp.analysis.model.AnalysisSummary;
import com.microapp.analysis.model.CategoryTotal;
import com.microapp.analysis.model.Insight;
import com.microapp.analysis.model.MerchantTotal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HeuristicCategorizer {

    private static final Logger log = LoggerFactory.getLogger(HeuristicCategorizer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Analyze statement JSON data that has already been fetched from the statement-service.
     */
    public static AnalysisSummary analyze(String accountId, String statementId, String statementJson) {
        try {
            JsonNode root = mapper.readTree(statementJson);
            JsonNode txnArray = root.get("transactions");

            if (txnArray == null || !txnArray.isArray()) {
                return new AnalysisSummary(accountId, statementId, 0, List.of(), List.of(), List.of());
            }

            // Aggregate by category
            Map<String, Double> categoryTotals = new LinkedHashMap<>();
            Map<String, Integer> categoryCounts = new LinkedHashMap<>();
            Map<String, Double> merchantTotals = new LinkedHashMap<>();
            Map<String, Integer> merchantCounts = new LinkedHashMap<>();
            double totalSpend = 0;

            for (JsonNode txn : txnArray) {
                String category = txn.get("category").asText();
                double amount = txn.get("amount").asDouble();
                String merchant = txn.get("merchant").asText();

                totalSpend += amount;
                categoryTotals.merge(category, amount, Double::sum);
                categoryCounts.merge(category, 1, Integer::sum);
                merchantTotals.merge(merchant, amount, Double::sum);
                merchantCounts.merge(merchant, 1, Integer::sum);
            }

            // Build category totals with percentages
            final double total = totalSpend;
            List<CategoryTotal> categories = categoryTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> new CategoryTotal(
                    e.getKey(),
                    Math.round(e.getValue() * 100.0) / 100.0,
                    categoryCounts.get(e.getKey()),
                    Math.round(e.getValue() / total * 10000.0) / 100.0
                ))
                .collect(Collectors.toList());

            // Top 5 merchants
            List<MerchantTotal> topMerchants = merchantTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(e -> new MerchantTotal(e.getKey(),
                    Math.round(e.getValue() * 100.0) / 100.0,
                    merchantCounts.get(e.getKey())))
                .collect(Collectors.toList());

            // Generate insights
            List<Insight> insights = new ArrayList<>();
            CategoryTotal topCategory = categories.isEmpty() ? null : categories.get(0);
            if (topCategory != null) {
                insights.add(new Insight("top_category",
                    topCategory.category() + " is your highest spending category at " +
                    topCategory.percentage() + "% of total spend ($" +
                    String.format("%.2f", topCategory.total()) + ")"));
            }

            if (categoryTotals.getOrDefault("Travel", 0.0) / total > 0.30) {
                insights.add(new Insight("travel_alert",
                    "Travel spending exceeds 30% of your total — consider a travel rewards card for maximum benefits"));
            }
            if (categoryTotals.getOrDefault("Health", 0.0) / total > 0.20) {
                insights.add(new Insight("health_alert",
                    "Significant health-related spending detected — a health coverage plan could save you money"));
            }
            if ((categoryTotals.getOrDefault("Groceries", 0.0) + categoryTotals.getOrDefault("Utilities", 0.0)) / total > 0.30) {
                insights.add(new Insight("everyday_tip",
                    "Groceries and utilities make up a large portion of spending — cashback cards offer savings here"));
            }

            // Recurring payment detection
            long recurringCount = merchantTotals.entrySet().stream()
                .filter(e -> merchantCounts.get(e.getKey()) >= 2 || e.getKey().contains("AT&T") || e.getKey().contains("Netflix"))
                .count();
            if (recurringCount > 0) {
                insights.add(new Insight("recurring",
                    recurringCount + " recurring payment(s) detected — automated bill pay can help avoid missed payments"));
            }

            AnalysisSummary summary = new AnalysisSummary(accountId, statementId,
                Math.round(total * 100.0) / 100.0, categories, topMerchants, insights);

            log.info("Analysis completed for account={} statement={}: {} categories, {} merchants, {} insights",
                accountId, statementId, categories.size(), topMerchants.size(), insights.size());

            return summary;

        } catch (Exception e) {
            log.error("Failed to analyze statement {} for account {}", statementId, accountId, e);
            return new AnalysisSummary(accountId, statementId, 0, List.of(), List.of(),
                List.of(new Insight("error", "Analysis failed: " + e.getMessage())));
        }
    }
}
