package com.microapp.payment.domain;

/** A saved payee the customer can transfer to (mock catalog for the demo). */
public record Beneficiary(
    String id,
    String name,
    String bank,
    String accountNumber
) {}
