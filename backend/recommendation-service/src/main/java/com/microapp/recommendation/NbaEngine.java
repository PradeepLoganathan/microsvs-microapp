package com.microapp.recommendation;

import com.microapp.recommendation.model.Nba;
import com.microapp.recommendation.model.NbaRequest;
import com.microapp.recommendation.model.NbaResponse;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-session next-best-action evaluator. Stateless rules — returns at most one
 * offer, chosen for the moment the user is currently looking at (e.g. they just
 * tapped a large transaction). Unlike RecommendationEngine, this is not a
 * dashboard list; it's the one card the app surfaces *right now*.
 */
public class NbaEngine {

  private static final Logger log = LoggerFactory.getLogger(NbaEngine.class);

  private static final Set<String> TRAVEL_MERCHANT_HINTS =
      Set.of("air", "airlines", "airways", "flight", "hotel", "agoda", "booking", "expedia", "klia");

  private static final double LARGE_TXN_THRESHOLD = 350.0;
  private static final double SALARY_THRESHOLD = 5_000.0;

  public static NbaResponse evaluate(String accountId, NbaRequest req) {
    if (req == null || req.trigger() == null || req.trigger().isBlank()) {
      return NbaResponse.none();
    }

    var match = switch (req.trigger()) {
      case NbaRequest.TRIGGER_LARGE_TXN -> evaluateLargeTransaction(req);
      case NbaRequest.TRIGGER_SALARY_CREDIT -> evaluateSalaryCredit(req);
      default -> null;
    };

    if (match != null) {
      log.info("NBA matched for account={} trigger={} productId={}", accountId, req.trigger(), match.productId());
      return NbaResponse.of(match);
    }
    log.info("NBA no match for account={} trigger={}", accountId, req.trigger());
    return NbaResponse.none();
  }

  private static Nba evaluateLargeTransaction(NbaRequest req) {
    double amount = req.amount() == null ? 0.0 : req.amount();
    if (amount < LARGE_TXN_THRESHOLD) return null;

    if (Boolean.TRUE.equals(req.overseas())) {
      return new Nba(
          "multi_currency_wallet",
          "Spending overseas? Hold MYR + 10 currencies in one wallet",
          "We saw a RM " + amount + " overseas charge"
              + (req.merchant() != null ? " at " + req.merchant() : "") + ".",
          "Open multi-currency wallet");
    }

    if (looksLikeTravel(req)) {
      return new Nba(
          "travel_takaful",
          "Travelling often? Shariah-compliant travel cover from RM 18/trip",
          "We saw a RM " + amount + " charge"
              + (req.merchant() != null ? " at " + req.merchant() : "")
              + " — travel takaful covers trip cancellation and medical abroad.",
          "See travel takaful");
    }

    return null;
  }

  private static Nba evaluateSalaryCredit(NbaRequest req) {
    double amount = req.amount() == null ? 0.0 : req.amount();
    if (amount < SALARY_THRESHOLD) return null;

    return new Nba(
        "tabung_hajj_autosave",
        "Auto-save RM 200 a month into Tabung Hajj?",
        "Your salary just hit. Splitting RM 200 into a goal each month reaches RM 12,000 in 5 years.",
        "Start a Tabung");
  }

  private static boolean looksLikeTravel(NbaRequest req) {
    if ("Travel".equalsIgnoreCase(req.category())) return true;
    if (req.merchant() == null) return false;
    var m = req.merchant().toLowerCase(Locale.ROOT);
    for (var hint : TRAVEL_MERCHANT_HINTS) {
      if (m.contains(hint)) return true;
    }
    return false;
  }
}
