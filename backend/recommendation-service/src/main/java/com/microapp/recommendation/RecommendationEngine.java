package com.microapp.recommendation;

import com.microapp.recommendation.model.Recommendation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class RecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEngine.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Generate recommendations from analysis summary JSON that has already been fetched.
     */
    public static List<Recommendation> generateRecommendations(String accountId, String statementId, String analysisJson) {
        try {
            JsonNode analysis = mapper.readTree(analysisJson);

            double totalSpend = analysis.get("totalSpend").asDouble();
            JsonNode categories = analysis.get("categories");
            JsonNode insights = analysis.get("insights");

            List<Recommendation> recommendations = new ArrayList<>();

            // Rule: high travel spending → travel rewards card
            double travelPct = getCategoryPercentage(categories, "Travel");
            if (travelPct > 30) {
                recommendations.add(new Recommendation(
                    "travel_rewards_card",
                    "Travel Rewards Platinum Card",
                    "Your travel spending is " + travelPct + "% of total — earn 3x points on every trip"
                ));
                log.info("Recommendation: travel_rewards_card (confidence: 0.92, travelPct: {}%)", travelPct);
            }

            // Rule: high health spending → health insurance
            double healthPct = getCategoryPercentage(categories, "Health");
            if (healthPct > 20) {
                recommendations.add(new Recommendation(
                    "health_insurance_basic",
                    "HealthGuard Basic Plan",
                    "With " + healthPct + "% spent on health, comprehensive coverage could reduce out-of-pocket costs"
                ));
                log.info("Recommendation: health_insurance_basic (confidence: 0.85, healthPct: {}%)", healthPct);
            }

            // Rule: high groceries + utilities → cashback card
            double groceryPct = getCategoryPercentage(categories, "Groceries");
            double utilityPct = getCategoryPercentage(categories, "Utilities");
            if (groceryPct + utilityPct > 30) {
                recommendations.add(new Recommendation(
                    "cashback_everyday_card",
                    "Cashback Everyday Card",
                    "Groceries and utilities are " + (groceryPct + utilityPct) + "% of spending — earn 2% cashback"
                ));
                log.info("Recommendation: cashback_everyday_card (confidence: 0.88, combinedPct: {}%)",
                    groceryPct + utilityPct);
            }

            // Rule: recurring payments → bill pay assistant
            boolean hasRecurring = false;
            if (insights != null) {
                for (JsonNode insight : insights) {
                    if ("recurring".equals(insight.get("type").asText())) {
                        hasRecurring = true;
                        break;
                    }
                }
            }
            if (hasRecurring) {
                recommendations.add(new Recommendation(
                    "bill_pay_assist",
                    "SmartBill Pay Assistant",
                    "Recurring payments detected — automate bills and never miss a due date"
                ));
                log.info("Recommendation: bill_pay_assist (confidence: 0.75)");
            }

            log.info("Generated {} recommendations for account={} statement={}",
                recommendations.size(), accountId, statementId);

            return recommendations;

        } catch (Exception e) {
            log.error("Failed to generate recommendations for {}/{}", accountId, statementId, e);
            return List.of();
        }
    }

    private static double getCategoryPercentage(JsonNode categories, String categoryName) {
        if (categories == null) return 0;
        for (JsonNode cat : categories) {
            if (categoryName.equals(cat.get("category").asText())) {
                return cat.get("percentage").asDouble();
            }
        }
        return 0;
    }
}
