package com.microapp.statement.api;

import com.microapp.statement.domain.Statement;
import com.microapp.statement.domain.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Provides seeded mock data for the demo.
 * Called by the seed endpoint to populate statement entities.
 */
public final class MockDataProvider {

  private MockDataProvider() {}

  /**
   * Flat monthly salary credit (totalCredits) so the advisor can derive a realistic
   * monthly savings surplus (credits - debits). Without it, surplus is always negative.
   * Plausible mid-career professional salary in Kuala Lumpur (MYR).
   */
  private static final double MONTHLY_INCOME = 9500.0;

  public static List<Statement> getAllStatements() {
    return List.of(decemberStatement(), januaryStatement(), februaryStatement());
  }

  /**
   * The 3-month dataset re-shaped for an arbitrary account — used to give a
   * newly-opened account realistic statements on demand. Each account gets a
   * different monthly income, scaled+jittered transaction amounts and a few
   * dropped transactions, so balances and totals differ across accounts.
   *
   * Deterministic: the variation is seeded by the account id, so re-seeding (or
   * recomputing balance) yields the same numbers for a given account. Statement
   * ids are a global key, so they're prefixed with the account id to stay unique.
   */
  public static List<Statement> getDemoStatementsFor(String accountId) {
    var rnd = new Random(accountId.hashCode());
    double monthlyIncome = 5000 + rnd.nextInt(11) * 1000;  // RM 5,000 – 15,000
    double spendFactor = 0.5 + rnd.nextDouble() * 0.9;     // 0.5x – 1.4x baseline spend

    return getAllStatements().stream()
        .map(s -> {
          // Reshape only the spending (debits); income is the account's own salary.
          var debits = s.transactions().stream()
              .filter(t -> !t.isCredit())
              .filter(t -> rnd.nextDouble() >= 0.2) // drop ~20% of transactions
              .map(t -> new Transaction(
                  t.id(), t.date(), t.merchant(),
                  round2(t.amount() * spendFactor * (0.85 + rnd.nextDouble() * 0.3)), // ±15% jitter
                  t.category(), t.description()))
              .toList();
          // Salary every month + an occasional transfer-in, varied per account.
          double transfer = rnd.nextDouble() < 0.5 ? round2(200 + rnd.nextDouble() * 1800) : 0.0;
          var credits = demoCredits(s.periodStart(), monthlyIncome, transfer);
          return assemble(
              "stmt-" + accountId + "-" + s.periodStart().substring(0, 7), // e.g. stmt-CASA-AB12-2025-12
              accountId, s.periodStart(), s.periodEnd(), credits, debits);
        })
        .toList();
  }

  /** Salary credit (+ optional transfer-in) for a month — money in, not spending. */
  private static List<Transaction> demoCredits(String periodStart, double salary, double transfer) {
    String month = periodStart.substring(0, 7);
    var credits = new ArrayList<Transaction>();
    credits.add(new Transaction("cr-sal-" + month, periodStart, "Salary Credit",
        round2(salary), "Income", "Monthly salary", "CREDIT"));
    if (transfer > 0) {
      credits.add(new Transaction("cr-trf-" + month, periodStart, "Transfer In",
          round2(transfer), "Transfer", "Transfer from another account", "CREDIT"));
    }
    return credits;
  }

  /** Combine credits + debits into a statement, deriving the credit/debit totals. */
  private static Statement assemble(String stmtId, String accountId, String start, String end,
                                    List<Transaction> credits, List<Transaction> debits) {
    var all = new ArrayList<Transaction>(credits);
    all.addAll(debits);
    double totalDebits = round2(debits.stream().mapToDouble(Transaction::amount).sum());
    double totalCredits = round2(credits.stream().mapToDouble(Transaction::amount).sum());
    return new Statement(stmtId, accountId, start, end, totalDebits, totalCredits, all);
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  private static Statement decemberStatement() {
    // December 2025 — travel-heavy month (year-end holiday)
    var txns = List.of(
        new Transaction("txn-001", "2025-12-02", "AirAsia", 850.00, "Travel", "Return flight KL-Bali"),
        new Transaction("txn-002", "2025-12-02", "Shangri-La Hotel KL", 420.00, "Travel", "1 night staycation"),
        new Transaction("txn-003", "2025-12-03", "Grab", 38.50, "Travel", "Airport transfer KLIA2"),
        new Transaction("txn-004", "2025-12-05", "Village Grocer", 165.80, "Groceries", "Weekly groceries"),
        new Transaction("txn-005", "2025-12-07", "Petronas", 95.20, "Utilities", "Petrol fill-up"),
        new Transaction("txn-006", "2025-12-10", "Agoda", 380.00, "Travel", "2 nights Penang hotel"),
        new Transaction("txn-007", "2025-12-12", "Klook", 145.50, "Travel", "Theme park tickets Genting"),
        new Transaction("txn-008", "2025-12-14", "Netflix", 54.90, "Entertainment", "Monthly subscription"),
        new Transaction("txn-009", "2025-12-15", "Malaysia Airlines", 520.00, "Travel", "One-way KL-Kota Kinabalu"),
        new Transaction("txn-010", "2025-12-18", "Pullman KL", 360.00, "Travel", "1 night business stay"),
        new Transaction("txn-011", "2025-12-20", "GrabFood", 48.50, "Dining", "Dinner delivery Bangsar"),
        new Transaction("txn-012", "2025-12-22", "Jaya Grocer", 132.30, "Groceries", "Holiday groceries"),
        new Transaction("txn-013", "2025-12-24", "Shopee", 218.00, "Shopping", "Christmas gifts"),
        new Transaction("txn-014", "2025-12-28", "Touch n Go", 50.00, "Travel", "Toll reload"),
        new Transaction("txn-015", "2025-12-30", "Maxis", 158.00, "Utilities", "Postpaid mobile bill")
    );
    return assemble("stmt-2025-12", "acc-1001", "2025-12-01", "2025-12-31",
        demoCredits("2025-12-01", MONTHLY_INCOME, 500.0), txns);
  }

  private static Statement januaryStatement() {
    // January 2026 — health-focused month (new year wellness kick)
    var txns = List.of(
        new Transaction("txn-101", "2026-01-02", "Caring Pharmacy", 88.50, "Health", "Prescription medications"),
        new Transaction("txn-102", "2026-01-03", "Fitness First KLCC", 245.00, "Health", "Monthly gym membership"),
        new Transaction("txn-103", "2026-01-05", "Jaya Grocer", 178.20, "Groceries", "Organic produce"),
        new Transaction("txn-104", "2026-01-07", "Pantai Hospital", 280.00, "Health", "GP consultation"),
        new Transaction("txn-105", "2026-01-08", "Guardian", 124.40, "Health", "Vitamins and supplements"),
        new Transaction("txn-106", "2026-01-10", "Tesco", 142.15, "Groceries", "Weekly groceries"),
        new Transaction("txn-107", "2026-01-12", "Watsons", 65.80, "Health", "OTC medicines"),
        new Transaction("txn-108", "2026-01-14", "Spotify", 16.90, "Entertainment", "Music subscription"),
        new Transaction("txn-109", "2026-01-15", "Klinik Pergigian Mediviron", 220.00, "Health", "Dental cleaning"),
        new Transaction("txn-110", "2026-01-18", "Village Grocer", 156.30, "Groceries", "Health foods"),
        new Transaction("txn-111", "2026-01-20", "Optical 88", 185.00, "Health", "Eye exam and lenses"),
        new Transaction("txn-112", "2026-01-22", "Tenaga Nasional", 215.00, "Utilities", "Electric bill"),
        new Transaction("txn-113", "2026-01-25", "Yoga Movement KL", 180.00, "Health", "Monthly yoga classes"),
        new Transaction("txn-114", "2026-01-27", "Madam Kwan's", 42.50, "Dining", "Lunch at Pavilion"),
        new Transaction("txn-115", "2026-01-30", "Celcom", 138.00, "Utilities", "Postpaid mobile bill")
    );
    return assemble("stmt-2026-01", "acc-1001", "2026-01-01", "2026-01-31",
        demoCredits("2026-01-01", MONTHLY_INCOME, 500.0), txns);
  }

  private static Statement februaryStatement() {
    // February 2026 — mixed month (utilities, groceries, entertainment, dining)
    var txns = List.of(
        new Transaction("txn-201", "2026-02-01", "Tenaga Nasional", 198.00, "Utilities", "Electric bill"),
        new Transaction("txn-202", "2026-02-02", "Cold Storage", 164.50, "Groceries", "Weekly groceries"),
        new Transaction("txn-203", "2026-02-03", "TGV Cinemas", 58.00, "Entertainment", "Movie night Mid Valley"),
        new Transaction("txn-204", "2026-02-05", "Nando's", 92.40, "Dining", "Dinner for two"),
        new Transaction("txn-205", "2026-02-07", "Aeon", 245.30, "Groceries", "Weekend big shop"),
        new Transaction("txn-206", "2026-02-08", "Unifi", 159.00, "Utilities", "Home fibre bill"),
        new Transaction("txn-207", "2026-02-10", "Old Town White Coffee", 38.50, "Dining", "Breakfast meetings"),
        new Transaction("txn-208", "2026-02-11", "Netflix", 54.90, "Entertainment", "Monthly subscription"),
        new Transaction("txn-209", "2026-02-12", "Air Selangor", 65.00, "Utilities", "Water bill"),
        new Transaction("txn-210", "2026-02-14", "Marini's on 57", 385.00, "Dining", "Valentine's dinner KLCC"),
        new Transaction("txn-211", "2026-02-15", "Lotus's", 118.70, "Groceries", "Weekly groceries"),
        new Transaction("txn-212", "2026-02-16", "Astro", 129.99, "Entertainment", "Pay TV subscription"),
        new Transaction("txn-213", "2026-02-17", "Shell", 110.50, "Utilities", "Petrol fill-up"),
        new Transaction("txn-214", "2026-02-18", "Kenny Rogers Roasters", 56.30, "Dining", "Family lunch"),
        new Transaction("txn-215", "2026-02-18", "Digi", 128.00, "Utilities", "Postpaid mobile bill")
    );
    return assemble("stmt-2026-02", "acc-1001", "2026-02-01", "2026-02-28",
        demoCredits("2026-02-01", MONTHLY_INCOME, 500.0), txns);
  }
}
