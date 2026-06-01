package com.microapp.advisor.domain;

import java.util.Map;

/**
 * Structured response from the wealth advisor agent.
 *
 * <p>The agent uses {@code responseConformsTo(AdvisorResponse.class)} so the model
 * output is forced into this shape. {@link ProposedAction#type()} is a plain
 * {@code String} (one of {@link ProposedAction.Type}'s constants) rather than a
 * Java enum: Akka's auto-generated JSON Schema for a nested enum field gets
 * over-described, and some LLMs then return the field as an object. A String +
 * a documented closed set is more robust and equally easy to guard against on
 * the read side.</p>
 *
 * <p>Pure domain — no Akka dependencies.</p>
 */
public record AdvisorResponse(
    String message,
    ProposedAction action,
    boolean needsHuman) {

  /** Conversational-only reply (no recommended action). */
  public static AdvisorResponse plain(String message) {
    return new AdvisorResponse(message, null, false);
  }

  /** Reply that defers to the human channel — no in-app action. */
  public static AdvisorResponse handoff(String message) {
    return new AdvisorResponse(message, null, true);
  }

  /**
   * One UI-renderable next step the agent recommends after this turn.
   * The frontend maps {@code type} deterministically to a navigation target
   * and uses {@code params} to pre-fill the destination form. A {@code null}
   * action means the bubble renders with no button.
   */
  public record ProposedAction(
      String type,
      String label,
      Map<String, String> params) {

    /** Closed set of valid {@code type} values. Documented here so the system
     *  prompt and the frontend can both reference one source of truth. */
    public static final class Type {
      public static final String CASA = "CASA";
      public static final String TAKAFUL = "TAKAFUL";
      public static final String TABUNG = "TABUNG";
      public static final String ADVISOR_HUMAN = "ADVISOR_HUMAN";
      public static final String NONE = "NONE";

      private Type() {}
    }

    public static ProposedAction tabung(String label, Map<String, String> params) {
      return new ProposedAction(Type.TABUNG, label, params);
    }

    public static ProposedAction casa(String label) {
      return new ProposedAction(Type.CASA, label, Map.of());
    }

    public static ProposedAction takaful(String label, Map<String, String> params) {
      return new ProposedAction(Type.TAKAFUL, label, params);
    }

    public static ProposedAction humanHandoff(String label) {
      return new ProposedAction(Type.ADVISOR_HUMAN, label, Map.of());
    }
  }
}
