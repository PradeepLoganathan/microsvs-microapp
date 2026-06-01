package com.microapp.recommendation.model;

/** Wrapper so a "no match" response is a well-typed empty rather than a 204. */
public record NbaResponse(boolean matched, Nba offer) {

  public static NbaResponse none() {
    return new NbaResponse(false, null);
  }

  public static NbaResponse of(Nba offer) {
    return new NbaResponse(true, offer);
  }
}
