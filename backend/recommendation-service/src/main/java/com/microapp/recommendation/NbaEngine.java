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
      Set.of("air", "airlines", "airways", "flight", "hotel", "agoda", "booking", "expedia", "klia",
             "resort", "shangri-la", "pullman", "marriott", "hilton", "mandarin");

  private static final Set<String> AIRLINE_HINTS =
      Set.of("airasia", "airlines", "airways", "firefly", "batik air", "flight");
  private static final Set<String> HOTEL_HINTS =
      Set.of("hotel", "resort", "shangri-la", "pullman", "marriott", "hilton", "mandarin", "majestic");
  private static final Set<String> BOOKING_HINTS =
      Set.of("agoda", "booking", "expedia", "klook", "trip.com");

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
      return travelTakaful(amount, req.merchant());
    }

    return null;
  }

  private static Nba travelTakaful(double amount, String merchant) {
    var m = merchant == null ? "" : merchant.toLowerCase(Locale.ROOT);
    String headline, reason;
    var atMerchant = (merchant != null && !merchant.isBlank()) ? " at " + merchant : "";

    if (containsAny(m, AIRLINE_HINTS)) {
      headline = "Flying often? Travel takaful from RM 18/trip";
      reason = "We saw a RM " + amount + " flight charge" + atMerchant
          + " — Shariah-compliant cover for flight delays, lost baggage and medical emergencies abroad.";
    } else if (containsAny(m, HOTEL_HINTS)) {
      headline = "Staying away from home? Travel takaful from RM 18/trip";
      reason = "We saw a RM " + amount + " hotel charge" + atMerchant
          + " — cover for stay cancellations, room theft and trip emergencies, Shariah-compliant.";
    } else if (containsAny(m, BOOKING_HINTS)) {
      headline = "Planning a trip? Travel takaful from RM 18/trip";
      reason = "We saw a RM " + amount + " booking" + atMerchant
          + " — protect your trip end-to-end against cancellations and emergencies.";
    } else {
      headline = "Travelling? Travel takaful from RM 18/trip";
      reason = "We saw a RM " + amount + " travel charge" + atMerchant
          + " — Shariah-compliant cover for cancellations, baggage and medical emergencies.";
    }
    return new Nba("travel_takaful", headline, reason, "See travel takaful");
  }

  private static boolean containsAny(String haystack, Set<String> needles) {
    for (var n : needles) if (haystack.contains(n)) return true;
    return false;
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
