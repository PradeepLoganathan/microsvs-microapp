package com.microapp.payment.domain;

import java.util.List;
import java.util.Optional;

/** Static mock beneficiaries for the demo. */
public final class BeneficiaryCatalog {

  private BeneficiaryCatalog() {}

  public static final List<Beneficiary> ALL = List.of(
      new Beneficiary("ben-ahmad", "Ahmad Zaki", "Maybank", "5141-2233-7788"),
      new Beneficiary("ben-siti", "Siti Nurhaliza", "CIMB Bank", "7009-1144-2255"),
      new Beneficiary("ben-nurul", "Nurul Izzah", "Touch 'n Go eWallet", "+60 12-345 6789"),
      new Beneficiary("ben-tnb", "Tenaga Nasional", "TNB Biller", "BILL-TNB-0001")
  );

  public static Optional<Beneficiary> byId(String id) {
    return ALL.stream().filter(b -> b.id().equals(id)).findFirst();
  }
}
