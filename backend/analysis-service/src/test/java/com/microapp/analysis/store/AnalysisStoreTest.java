package com.microapp.analysis.store;

import com.microapp.analysis.model.AnalysisSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisStoreTest {

    @Test
    void shouldSaveAndRetrieveSummary() {
        var summary = new AnalysisSummary("acc-test", "stmt-test", 1000.0,
            List.of(), List.of(), List.of());

        AnalysisStore.save("acc-test", "stmt-test", summary);
        AnalysisSummary retrieved = AnalysisStore.get("acc-test", "stmt-test");

        assertNotNull(retrieved);
        assertEquals("acc-test", retrieved.accountId());
        assertEquals("stmt-test", retrieved.statementId());
        assertEquals(1000.0, retrieved.totalSpend());
    }

    @Test
    void shouldReturnNullForMissingEntry() {
        AnalysisSummary result = AnalysisStore.get("nonexistent", "nonexistent");
        assertNull(result);
    }

    @Test
    void shouldOverwriteExistingEntry() {
        var summary1 = new AnalysisSummary("acc-ow", "stmt-ow", 500.0,
            List.of(), List.of(), List.of());
        var summary2 = new AnalysisSummary("acc-ow", "stmt-ow", 750.0,
            List.of(), List.of(), List.of());

        AnalysisStore.save("acc-ow", "stmt-ow", summary1);
        AnalysisStore.save("acc-ow", "stmt-ow", summary2);

        AnalysisSummary retrieved = AnalysisStore.get("acc-ow", "stmt-ow");
        assertEquals(750.0, retrieved.totalSpend());
    }
}
