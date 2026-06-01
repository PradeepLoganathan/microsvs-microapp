package com.microapp.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.microapp.recommendation.model.NbaRequest;
import org.junit.jupiter.api.Test;

public class NbaEngineTest {

  private static final String ACCOUNT_ID = "acc-1001";

  @Test
  public void overseasLargeTxnSuggestsMultiCurrency() {
    var req = new NbaRequest(
        NbaRequest.TRIGGER_LARGE_TXN, "tx-1", 850.0, "Tokyu Hands Tokyo", "Shopping", true);

    var resp = NbaEngine.evaluate(ACCOUNT_ID, req);

    assertThat(resp.matched()).isTrue();
    assertThat(resp.offer().productId()).isEqualTo("multi_currency_wallet");
    assertThat(resp.offer().reason()).contains("overseas").contains("Tokyu Hands Tokyo");
  }

  @Test
  public void travelMerchantSuggestsTravelTakaful() {
    var req = new NbaRequest(
        NbaRequest.TRIGGER_LARGE_TXN, "tx-2", 850.0, "Air Asia", "Travel", false);

    var resp = NbaEngine.evaluate(ACCOUNT_ID, req);

    assertThat(resp.matched()).isTrue();
    assertThat(resp.offer().productId()).isEqualTo("travel_takaful");
    assertThat(resp.offer().reason()).contains("Air Asia");
  }

  @Test
  public void travelByCategoryAloneMatches() {
    var req = new NbaRequest(NbaRequest.TRIGGER_LARGE_TXN, "tx-3", 600.0, "Some Agency", "Travel", false);

    var resp = NbaEngine.evaluate(ACCOUNT_ID, req);

    assertThat(resp.matched()).isTrue();
    assertThat(resp.offer().productId()).isEqualTo("travel_takaful");
  }

  @Test
  public void overseasBeatsTravelMatch() {
    var req = new NbaRequest(NbaRequest.TRIGGER_LARGE_TXN, "tx-4", 1_200.0, "Air Asia", "Travel", true);

    var resp = NbaEngine.evaluate(ACCOUNT_ID, req);

    assertThat(resp.offer().productId()).isEqualTo("multi_currency_wallet");
  }

  @Test
  public void largeTxnBelowThresholdDoesNotMatch() {
    var req = new NbaRequest(NbaRequest.TRIGGER_LARGE_TXN, "tx-5", 100.0, "Cafe", "Food", false);

    var resp = NbaEngine.evaluate(ACCOUNT_ID, req);

    assertThat(resp.matched()).isFalse();
    assertThat(resp.offer()).isNull();
  }

  @Test
  public void largeTxnGenericMerchantDoesNotMatch() {
    // > threshold, but no travel hint and not overseas — no rule fires
    var req = new NbaRequest(NbaRequest.TRIGGER_LARGE_TXN, "tx-6", 900.0, "Tesco", "Groceries", false);

    var resp = NbaEngine.evaluate(ACCOUNT_ID, req);

    assertThat(resp.matched()).isFalse();
  }

  @Test
  public void salaryCreditSuggestsTabungHajj() {
    var req = new NbaRequest(NbaRequest.TRIGGER_SALARY_CREDIT, "tx-7", 8_000.0, "Acme Sdn Bhd", null, null);

    var resp = NbaEngine.evaluate(ACCOUNT_ID, req);

    assertThat(resp.matched()).isTrue();
    assertThat(resp.offer().productId()).isEqualTo("tabung_hajj_autosave");
  }

  @Test
  public void salaryCreditBelowThresholdDoesNotMatch() {
    var req = new NbaRequest(NbaRequest.TRIGGER_SALARY_CREDIT, "tx-8", 1_000.0, "Acme", null, null);

    var resp = NbaEngine.evaluate(ACCOUNT_ID, req);

    assertThat(resp.matched()).isFalse();
  }

  @Test
  public void unknownTriggerReturnsNoMatch() {
    var req = new NbaRequest("MYSTERY_TRIGGER", "tx-9", 5_000.0, "Anywhere", "X", null);

    var resp = NbaEngine.evaluate(ACCOUNT_ID, req);

    assertThat(resp.matched()).isFalse();
  }

  @Test
  public void nullOrBlankTriggerReturnsNoMatch() {
    assertThat(NbaEngine.evaluate(ACCOUNT_ID, null).matched()).isFalse();

    var blank = new NbaRequest("", "tx-10", 5_000.0, "X", "X", null);
    assertThat(NbaEngine.evaluate(ACCOUNT_ID, blank).matched()).isFalse();
  }
}
