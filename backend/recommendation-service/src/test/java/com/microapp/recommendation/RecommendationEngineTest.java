package com.microapp.recommendation;

import com.microapp.recommendation.model.Recommendation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationEngineTest {

    private static final String ACCOUNT_ID = "acc-1001";
    private static final String STATEMENT_ID = "stmt-2025-12";

    @Test
    void shouldRecommendTravelCardForHighTravelSpend() {
        String analysisJson = """
            {
              "accountId": "acc-1001",
              "statementId": "stmt-2025-12",
              "totalSpend": 1000.0,
              "categories": [
                {"category": "Travel", "total": 500.0, "count": 3, "percentage": 50.0},
                {"category": "Groceries", "total": 500.0, "count": 5, "percentage": 50.0}
              ],
              "topMerchants": [],
              "insights": [
                {"type": "top_category", "message": "Travel is your highest spending category"}
              ]
            }
            """;

        List<Recommendation> recs = RecommendationEngine.generateRecommendations(
            ACCOUNT_ID, STATEMENT_ID, analysisJson);

        assertTrue(recs.stream().anyMatch(r -> "travel_rewards_card".equals(r.productId())));
    }

    @Test
    void shouldRecommendHealthInsuranceForHighHealthSpend() {
        String analysisJson = """
            {
              "accountId": "acc-1001",
              "statementId": "stmt-2025-12",
              "totalSpend": 1000.0,
              "categories": [
                {"category": "Health", "total": 300.0, "count": 4, "percentage": 30.0},
                {"category": "Groceries", "total": 700.0, "count": 10, "percentage": 70.0}
              ],
              "topMerchants": [],
              "insights": []
            }
            """;

        List<Recommendation> recs = RecommendationEngine.generateRecommendations(
            ACCOUNT_ID, STATEMENT_ID, analysisJson);

        assertTrue(recs.stream().anyMatch(r -> "health_insurance_basic".equals(r.productId())));
    }

    @Test
    void shouldRecommendCashbackForHighGroceryAndUtilitySpend() {
        String analysisJson = """
            {
              "accountId": "acc-1001",
              "statementId": "stmt-2025-12",
              "totalSpend": 1000.0,
              "categories": [
                {"category": "Groceries", "total": 200.0, "count": 5, "percentage": 20.0},
                {"category": "Utilities", "total": 150.0, "count": 3, "percentage": 15.0},
                {"category": "Entertainment", "total": 650.0, "count": 8, "percentage": 65.0}
              ],
              "topMerchants": [],
              "insights": []
            }
            """;

        List<Recommendation> recs = RecommendationEngine.generateRecommendations(
            ACCOUNT_ID, STATEMENT_ID, analysisJson);

        assertTrue(recs.stream().anyMatch(r -> "cashback_everyday_card".equals(r.productId())));
    }

    @Test
    void shouldRecommendBillPayForRecurringPayments() {
        String analysisJson = """
            {
              "accountId": "acc-1001",
              "statementId": "stmt-2025-12",
              "totalSpend": 500.0,
              "categories": [
                {"category": "Entertainment", "total": 500.0, "count": 5, "percentage": 100.0}
              ],
              "topMerchants": [],
              "insights": [
                {"type": "recurring", "message": "3 recurring payments detected"}
              ]
            }
            """;

        List<Recommendation> recs = RecommendationEngine.generateRecommendations(
            ACCOUNT_ID, STATEMENT_ID, analysisJson);

        assertTrue(recs.stream().anyMatch(r -> "bill_pay_assist".equals(r.productId())));
    }

    @Test
    void shouldNotRecommendTravelCardBelowThreshold() {
        // Travel at exactly 30% — threshold is > 30, so should NOT trigger
        String analysisJson = """
            {
              "accountId": "acc-1001",
              "statementId": "stmt-2025-12",
              "totalSpend": 1000.0,
              "categories": [
                {"category": "Travel", "total": 300.0, "count": 2, "percentage": 30.0},
                {"category": "Groceries", "total": 700.0, "count": 8, "percentage": 70.0}
              ],
              "topMerchants": [],
              "insights": []
            }
            """;

        List<Recommendation> recs = RecommendationEngine.generateRecommendations(
            ACCOUNT_ID, STATEMENT_ID, analysisJson);

        assertFalse(recs.stream().anyMatch(r -> "travel_rewards_card".equals(r.productId())));
    }

    @Test
    void shouldNotRecommendHealthInsuranceBelowThreshold() {
        // Health at 20% — threshold is > 20, so should NOT trigger
        String analysisJson = """
            {
              "accountId": "acc-1001",
              "statementId": "stmt-2025-12",
              "totalSpend": 1000.0,
              "categories": [
                {"category": "Health", "total": 200.0, "count": 3, "percentage": 20.0},
                {"category": "Groceries", "total": 800.0, "count": 10, "percentage": 80.0}
              ],
              "topMerchants": [],
              "insights": []
            }
            """;

        List<Recommendation> recs = RecommendationEngine.generateRecommendations(
            ACCOUNT_ID, STATEMENT_ID, analysisJson);

        assertFalse(recs.stream().anyMatch(r -> "health_insurance_basic".equals(r.productId())));
    }

    @Test
    void shouldGenerateMultipleRecommendations() {
        // Travel > 30%, recurring present → two recommendations
        String analysisJson = """
            {
              "accountId": "acc-1001",
              "statementId": "stmt-2025-12",
              "totalSpend": 1000.0,
              "categories": [
                {"category": "Travel", "total": 600.0, "count": 4, "percentage": 60.0},
                {"category": "Entertainment", "total": 400.0, "count": 5, "percentage": 40.0}
              ],
              "topMerchants": [],
              "insights": [
                {"type": "top_category", "message": "Travel is top"},
                {"type": "travel_alert", "message": "Travel exceeds 30%"},
                {"type": "recurring", "message": "2 recurring payments detected"}
              ]
            }
            """;

        List<Recommendation> recs = RecommendationEngine.generateRecommendations(
            ACCOUNT_ID, STATEMENT_ID, analysisJson);

        assertEquals(2, recs.size());
        assertTrue(recs.stream().anyMatch(r -> "travel_rewards_card".equals(r.productId())));
        assertTrue(recs.stream().anyMatch(r -> "bill_pay_assist".equals(r.productId())));
    }

    @Test
    void shouldReturnEmptyListForNoMatchingRules() {
        String analysisJson = """
            {
              "accountId": "acc-1001",
              "statementId": "stmt-2025-12",
              "totalSpend": 100.0,
              "categories": [
                {"category": "Entertainment", "total": 100.0, "count": 2, "percentage": 100.0}
              ],
              "topMerchants": [],
              "insights": [
                {"type": "top_category", "message": "Entertainment is top"}
              ]
            }
            """;

        List<Recommendation> recs = RecommendationEngine.generateRecommendations(
            ACCOUNT_ID, STATEMENT_ID, analysisJson);

        assertTrue(recs.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForInvalidJson() {
        List<Recommendation> recs = RecommendationEngine.generateRecommendations(
            ACCOUNT_ID, STATEMENT_ID, "not valid json");

        assertTrue(recs.isEmpty());
    }

    @Test
    void shouldIncludeReasonInRecommendation() {
        String analysisJson = """
            {
              "accountId": "acc-1001",
              "statementId": "stmt-2025-12",
              "totalSpend": 1000.0,
              "categories": [
                {"category": "Travel", "total": 500.0, "count": 3, "percentage": 50.0}
              ],
              "topMerchants": [],
              "insights": []
            }
            """;

        List<Recommendation> recs = RecommendationEngine.generateRecommendations(
            ACCOUNT_ID, STATEMENT_ID, analysisJson);

        Recommendation travelRec = recs.stream()
            .filter(r -> "travel_rewards_card".equals(r.productId()))
            .findFirst()
            .orElseThrow();

        assertEquals("Travel Rewards Platinum Card", travelRec.productName());
        assertTrue(travelRec.reason().contains("50.0%"));
    }

    @Test
    void shouldHandleNullInsights() {
        // insights field missing from JSON
        String analysisJson = """
            {
              "accountId": "acc-1001",
              "statementId": "stmt-2025-12",
              "totalSpend": 1000.0,
              "categories": [
                {"category": "Travel", "total": 500.0, "count": 3, "percentage": 50.0}
              ],
              "topMerchants": []
            }
            """;

        List<Recommendation> recs = RecommendationEngine.generateRecommendations(
            ACCOUNT_ID, STATEMENT_ID, analysisJson);

        // Should still get travel recommendation, but not bill_pay_assist
        assertTrue(recs.stream().anyMatch(r -> "travel_rewards_card".equals(r.productId())));
        assertFalse(recs.stream().anyMatch(r -> "bill_pay_assist".equals(r.productId())));
    }
}
