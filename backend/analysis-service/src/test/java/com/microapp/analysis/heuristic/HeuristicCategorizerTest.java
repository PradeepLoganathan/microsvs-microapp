package com.microapp.analysis.heuristic;

import com.microapp.analysis.model.AnalysisSummary;
import com.microapp.analysis.model.CategoryTotal;
import com.microapp.analysis.model.Insight;
import com.microapp.analysis.model.MerchantTotal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeuristicCategorizerTest {

    private static final String ACCOUNT_ID = "acc-1001";
    private static final String STATEMENT_ID = "stmt-2025-12";

    @Test
    void shouldAnalyzeTravelHeavyStatement() {
        String json = """
            {
              "statementId": "stmt-2025-12",
              "accountId": "acc-1001",
              "transactions": [
                {"id":"txn-001","date":"2025-12-02","merchant":"Delta Airlines","amount":487.50,"category":"Travel","description":"Round trip ATL-LAX"},
                {"id":"txn-002","date":"2025-12-03","merchant":"Marriott Hotels","amount":312.00,"category":"Travel","description":"2-night stay"},
                {"id":"txn-003","date":"2025-12-04","merchant":"Uber","amount":45.00,"category":"Travel","description":"Airport transfer"},
                {"id":"txn-004","date":"2025-12-05","merchant":"Delta Airlines","amount":487.50,"category":"Travel","description":"Return flight"},
                {"id":"txn-005","date":"2025-12-10","merchant":"Walmart","amount":120.00,"category":"Groceries","description":"Weekly groceries"},
                {"id":"txn-006","date":"2025-12-15","merchant":"AT&T","amount":85.00,"category":"Utilities","description":"Phone bill"}
              ]
            }
            """;

        AnalysisSummary result = HeuristicCategorizer.analyze(ACCOUNT_ID, STATEMENT_ID, json);

        assertEquals(ACCOUNT_ID, result.accountId());
        assertEquals(STATEMENT_ID, result.statementId());
        assertEquals(1537.0, result.totalSpend());

        // Travel should be the top category
        assertFalse(result.categories().isEmpty());
        CategoryTotal topCategory = result.categories().get(0);
        assertEquals("Travel", topCategory.category());
        assertEquals(4, topCategory.count());

        // Top merchants should include Delta Airlines
        assertFalse(result.topMerchants().isEmpty());
        MerchantTotal topMerchant = result.topMerchants().get(0);
        assertEquals("Delta Airlines", topMerchant.merchant());
        assertEquals(975.0, topMerchant.total());
        assertEquals(2, topMerchant.count());
    }

    @Test
    void shouldGenerateTravelAlert() {
        // Travel > 30% of total
        String json = """
            {
              "transactions": [
                {"id":"1","date":"2025-12-01","merchant":"Delta","amount":700.00,"category":"Travel","description":"Flight"},
                {"id":"2","date":"2025-12-02","merchant":"Walmart","amount":100.00,"category":"Groceries","description":"Food"}
              ]
            }
            """;

        AnalysisSummary result = HeuristicCategorizer.analyze(ACCOUNT_ID, STATEMENT_ID, json);

        List<String> insightTypes = result.insights().stream().map(Insight::type).toList();
        assertTrue(insightTypes.contains("travel_alert"), "Expected travel_alert insight");
        assertTrue(insightTypes.contains("top_category"), "Expected top_category insight");
    }

    @Test
    void shouldGenerateHealthAlert() {
        // Health > 20% of total
        String json = """
            {
              "transactions": [
                {"id":"1","date":"2025-12-01","merchant":"CVS Pharmacy","amount":250.00,"category":"Health","description":"Prescription"},
                {"id":"2","date":"2025-12-02","merchant":"Dr Smith","amount":200.00,"category":"Health","description":"Doctor visit"},
                {"id":"3","date":"2025-12-03","merchant":"Walmart","amount":500.00,"category":"Groceries","description":"Monthly groceries"}
              ]
            }
            """;

        AnalysisSummary result = HeuristicCategorizer.analyze(ACCOUNT_ID, STATEMENT_ID, json);

        List<String> insightTypes = result.insights().stream().map(Insight::type).toList();
        assertTrue(insightTypes.contains("health_alert"), "Expected health_alert insight");
    }

    @Test
    void shouldGenerateEverydayTip() {
        // Groceries + Utilities > 30% of total
        String json = """
            {
              "transactions": [
                {"id":"1","date":"2025-12-01","merchant":"Walmart","amount":200.00,"category":"Groceries","description":"Groceries"},
                {"id":"2","date":"2025-12-02","merchant":"AT&T","amount":100.00,"category":"Utilities","description":"Phone"},
                {"id":"3","date":"2025-12-03","merchant":"Netflix","amount":15.00,"category":"Entertainment","description":"Subscription"}
              ]
            }
            """;

        AnalysisSummary result = HeuristicCategorizer.analyze(ACCOUNT_ID, STATEMENT_ID, json);

        List<String> insightTypes = result.insights().stream().map(Insight::type).toList();
        assertTrue(insightTypes.contains("everyday_tip"), "Expected everyday_tip insight");
    }

    @Test
    void shouldDetectRecurringPayments() {
        // Same merchant appearing 2+ times = recurring
        String json = """
            {
              "transactions": [
                {"id":"1","date":"2025-12-01","merchant":"Netflix","amount":15.99,"category":"Entertainment","description":"Subscription"},
                {"id":"2","date":"2025-12-15","merchant":"Netflix","amount":15.99,"category":"Entertainment","description":"Subscription"},
                {"id":"3","date":"2025-12-10","merchant":"Walmart","amount":150.00,"category":"Groceries","description":"Groceries"}
              ]
            }
            """;

        AnalysisSummary result = HeuristicCategorizer.analyze(ACCOUNT_ID, STATEMENT_ID, json);

        List<String> insightTypes = result.insights().stream().map(Insight::type).toList();
        assertTrue(insightTypes.contains("recurring"), "Expected recurring insight");
    }

    @Test
    void shouldLimitTopMerchantsToFive() {
        String json = """
            {
              "transactions": [
                {"id":"1","date":"2025-12-01","merchant":"Merchant A","amount":100,"category":"Shopping","description":"A"},
                {"id":"2","date":"2025-12-02","merchant":"Merchant B","amount":90,"category":"Shopping","description":"B"},
                {"id":"3","date":"2025-12-03","merchant":"Merchant C","amount":80,"category":"Shopping","description":"C"},
                {"id":"4","date":"2025-12-04","merchant":"Merchant D","amount":70,"category":"Shopping","description":"D"},
                {"id":"5","date":"2025-12-05","merchant":"Merchant E","amount":60,"category":"Shopping","description":"E"},
                {"id":"6","date":"2025-12-06","merchant":"Merchant F","amount":50,"category":"Shopping","description":"F"},
                {"id":"7","date":"2025-12-07","merchant":"Merchant G","amount":40,"category":"Shopping","description":"G"}
              ]
            }
            """;

        AnalysisSummary result = HeuristicCategorizer.analyze(ACCOUNT_ID, STATEMENT_ID, json);

        assertEquals(5, result.topMerchants().size());
        assertEquals("Merchant A", result.topMerchants().get(0).merchant());
        assertEquals("Merchant E", result.topMerchants().get(4).merchant());
    }

    @Test
    void shouldSortCategoriesByTotalDescending() {
        String json = """
            {
              "transactions": [
                {"id":"1","date":"2025-12-01","merchant":"Walmart","amount":50,"category":"Groceries","description":"Food"},
                {"id":"2","date":"2025-12-02","merchant":"Delta","amount":500,"category":"Travel","description":"Flight"},
                {"id":"3","date":"2025-12-03","merchant":"AMC","amount":20,"category":"Entertainment","description":"Movie"}
              ]
            }
            """;

        AnalysisSummary result = HeuristicCategorizer.analyze(ACCOUNT_ID, STATEMENT_ID, json);

        assertEquals(3, result.categories().size());
        assertEquals("Travel", result.categories().get(0).category());
        assertEquals("Groceries", result.categories().get(1).category());
        assertEquals("Entertainment", result.categories().get(2).category());
    }

    @Test
    void shouldCalculatePercentagesCorrectly() {
        String json = """
            {
              "transactions": [
                {"id":"1","date":"2025-12-01","merchant":"Delta","amount":600,"category":"Travel","description":"Flight"},
                {"id":"2","date":"2025-12-02","merchant":"Walmart","amount":400,"category":"Groceries","description":"Food"}
              ]
            }
            """;

        AnalysisSummary result = HeuristicCategorizer.analyze(ACCOUNT_ID, STATEMENT_ID, json);

        assertEquals(1000.0, result.totalSpend());
        CategoryTotal travel = result.categories().stream()
            .filter(c -> "Travel".equals(c.category())).findFirst().orElseThrow();
        CategoryTotal groceries = result.categories().stream()
            .filter(c -> "Groceries".equals(c.category())).findFirst().orElseThrow();

        assertEquals(60.0, travel.percentage());
        assertEquals(40.0, groceries.percentage());
    }

    @Test
    void shouldHandleEmptyTransactions() {
        String json = """
            {
              "transactions": []
            }
            """;

        AnalysisSummary result = HeuristicCategorizer.analyze(ACCOUNT_ID, STATEMENT_ID, json);

        assertEquals(0, result.totalSpend());
        assertTrue(result.categories().isEmpty());
        assertTrue(result.topMerchants().isEmpty());
        assertTrue(result.insights().isEmpty());
    }

    @Test
    void shouldHandleMissingTransactionsField() {
        String json = """
            {
              "statementId": "stmt-2025-12",
              "accountId": "acc-1001"
            }
            """;

        AnalysisSummary result = HeuristicCategorizer.analyze(ACCOUNT_ID, STATEMENT_ID, json);

        assertEquals(0, result.totalSpend());
        assertTrue(result.categories().isEmpty());
    }

    @Test
    void shouldHandleInvalidJson() {
        String json = "not valid json at all";

        AnalysisSummary result = HeuristicCategorizer.analyze(ACCOUNT_ID, STATEMENT_ID, json);

        assertEquals(ACCOUNT_ID, result.accountId());
        assertEquals(STATEMENT_ID, result.statementId());
        assertEquals(0, result.totalSpend());
        assertFalse(result.insights().isEmpty());
        assertEquals("error", result.insights().get(0).type());
    }

    @Test
    void shouldHandleSingleTransaction() {
        String json = """
            {
              "transactions": [
                {"id":"1","date":"2025-12-01","merchant":"Delta","amount":500,"category":"Travel","description":"Flight"}
              ]
            }
            """;

        AnalysisSummary result = HeuristicCategorizer.analyze(ACCOUNT_ID, STATEMENT_ID, json);

        assertEquals(500.0, result.totalSpend());
        assertEquals(1, result.categories().size());
        assertEquals(100.0, result.categories().get(0).percentage());
        assertEquals(1, result.topMerchants().size());
    }
}
