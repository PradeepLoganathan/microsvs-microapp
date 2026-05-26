package com.microapp.customer.domain;

import com.microapp.customer.domain.Customer.Registration;
import com.microapp.customer.domain.Customer.WelcomeOffer;

/**
 * Local, deterministic welcome-offer rule. A brand-new registrant has no spending
 * history, so the offer is keyed on the registration channel (no recommendation-service
 * call). Pure domain logic.
 */
public final class WelcomeOfferRules {

  private WelcomeOfferRules() {}

  public static WelcomeOffer forRegistration(Registration r) {
    var channel = r.channel() == null ? "" : r.channel().toUpperCase();
    return switch (channel) {
      case "REFERRAL" -> new WelcomeOffer(
          "WELCOME-REF50", "Referral Bonus",
          "RM50 cash credit when you fund your first account.", "casa-savings", 50.0);
      case "MOBILE" -> new WelcomeOffer(
          "WELCOME-APP25", "App Sign-up Reward",
          "RM25 e-wallet top-up for joining on the app.", "casa-savings", 25.0);
      default -> new WelcomeOffer(
          "WELCOME-START", "Starter Savings",
          "Fee-free CASA account plus an RM10 welcome credit.", "casa-savings", 10.0);
    };
  }
}
