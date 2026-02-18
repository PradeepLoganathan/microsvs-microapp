package com.microapp.statement.api;

import com.microapp.statement.domain.Statement;
import com.microapp.statement.domain.Transaction;

import java.util.List;

/**
 * Provides seeded mock data for the demo.
 * Called by the seed endpoint to populate statement entities.
 */
public final class MockDataProvider {

  private MockDataProvider() {}

  public static List<Statement> getAllStatements() {
    return List.of(decemberStatement(), januaryStatement(), februaryStatement());
  }

  private static Statement decemberStatement() {
    // December 2025 — travel-heavy month
    var txns = List.of(
        new Transaction("txn-001", "2025-12-02", "Delta Airlines", 487.50, "Travel", "Round trip ATL-LAX"),
        new Transaction("txn-002", "2025-12-02", "Marriott Hotels", 312.00, "Travel", "3 nights Los Angeles"),
        new Transaction("txn-003", "2025-12-03", "Uber", 42.30, "Travel", "Airport transfer LAX"),
        new Transaction("txn-004", "2025-12-05", "Whole Foods", 67.80, "Groceries", "Weekly groceries"),
        new Transaction("txn-005", "2025-12-07", "Shell Gas Station", 55.20, "Utilities", "Fuel"),
        new Transaction("txn-006", "2025-12-10", "Airbnb", 198.00, "Travel", "Weekend cabin rental"),
        new Transaction("txn-007", "2025-12-12", "Duty Free Shop", 89.50, "Travel", "Airport shopping"),
        new Transaction("txn-008", "2025-12-14", "Netflix", 15.99, "Entertainment", "Monthly subscription"),
        new Transaction("txn-009", "2025-12-15", "United Airlines", 352.00, "Travel", "One-way SFO-ORD"),
        new Transaction("txn-010", "2025-12-18", "Hilton Hotels", 245.00, "Travel", "2 nights Chicago"),
        new Transaction("txn-011", "2025-12-20", "Uber Eats", 34.50, "Dining", "Dinner delivery"),
        new Transaction("txn-012", "2025-12-22", "Trader Joe's", 52.30, "Groceries", "Holiday groceries"),
        new Transaction("txn-013", "2025-12-24", "Amazon", 125.00, "Shopping", "Holiday gifts"),
        new Transaction("txn-014", "2025-12-28", "Lyft", 28.75, "Travel", "City ride"),
        new Transaction("txn-015", "2025-12-30", "AT&T", 85.00, "Utilities", "Phone bill")
    );
    double total = txns.stream().mapToDouble(Transaction::amount).sum();
    return new Statement("stmt-2025-12", "acc-1001", "2025-12-01", "2025-12-31", total, 0, txns);
  }

  private static Statement januaryStatement() {
    // January 2026 — health-focused month
    var txns = List.of(
        new Transaction("txn-101", "2026-01-02", "CVS Pharmacy", 67.50, "Health", "Prescription medications"),
        new Transaction("txn-102", "2026-01-03", "Planet Fitness", 49.99, "Health", "Monthly gym membership"),
        new Transaction("txn-103", "2026-01-05", "Whole Foods", 89.20, "Groceries", "Organic produce"),
        new Transaction("txn-104", "2026-01-07", "Dr. Smith Office", 150.00, "Health", "Annual checkup copay"),
        new Transaction("txn-105", "2026-01-08", "GNC Nutrition", 78.40, "Health", "Vitamins and supplements"),
        new Transaction("txn-106", "2026-01-10", "Kroger", 62.15, "Groceries", "Weekly groceries"),
        new Transaction("txn-107", "2026-01-12", "Walgreens", 35.80, "Health", "OTC medicines"),
        new Transaction("txn-108", "2026-01-14", "Spotify", 10.99, "Entertainment", "Music subscription"),
        new Transaction("txn-109", "2026-01-15", "Dental Care Plus", 120.00, "Health", "Dental cleaning"),
        new Transaction("txn-110", "2026-01-18", "Trader Joe's", 71.30, "Groceries", "Health foods"),
        new Transaction("txn-111", "2026-01-20", "Vision Center", 95.00, "Health", "Eye exam"),
        new Transaction("txn-112", "2026-01-22", "Duke Energy", 142.00, "Utilities", "Electric bill"),
        new Transaction("txn-113", "2026-01-25", "Yoga Studio", 80.00, "Health", "Monthly yoga classes"),
        new Transaction("txn-114", "2026-01-27", "Chipotle", 14.50, "Dining", "Lunch"),
        new Transaction("txn-115", "2026-01-30", "AT&T", 85.00, "Utilities", "Phone bill")
    );
    double total = txns.stream().mapToDouble(Transaction::amount).sum();
    return new Statement("stmt-2026-01", "acc-1001", "2026-01-01", "2026-01-31", total, 0, txns);
  }

  private static Statement februaryStatement() {
    // February 2026 — mixed month (utilities, groceries, entertainment, dining)
    var txns = List.of(
        new Transaction("txn-201", "2026-02-01", "Duke Energy", 138.00, "Utilities", "Electric bill"),
        new Transaction("txn-202", "2026-02-02", "Whole Foods", 94.50, "Groceries", "Weekly groceries"),
        new Transaction("txn-203", "2026-02-03", "AMC Theaters", 32.00, "Entertainment", "Movie night"),
        new Transaction("txn-204", "2026-02-05", "Olive Garden", 68.40, "Dining", "Dinner for two"),
        new Transaction("txn-205", "2026-02-07", "Costco", 185.30, "Groceries", "Bulk shopping"),
        new Transaction("txn-206", "2026-02-08", "Comcast", 79.99, "Utilities", "Internet bill"),
        new Transaction("txn-207", "2026-02-10", "Starbucks", 24.50, "Dining", "Coffee runs"),
        new Transaction("txn-208", "2026-02-11", "Netflix", 15.99, "Entertainment", "Monthly subscription"),
        new Transaction("txn-209", "2026-02-12", "Water Authority", 45.00, "Utilities", "Water bill"),
        new Transaction("txn-210", "2026-02-14", "Ruth's Chris", 142.00, "Dining", "Valentine's dinner"),
        new Transaction("txn-211", "2026-02-15", "Kroger", 58.70, "Groceries", "Weekly groceries"),
        new Transaction("txn-212", "2026-02-16", "Hulu", 17.99, "Entertainment", "Streaming subscription"),
        new Transaction("txn-213", "2026-02-17", "Shell Gas Station", 48.50, "Utilities", "Fuel"),
        new Transaction("txn-214", "2026-02-18", "Panera Bread", 22.30, "Dining", "Lunch"),
        new Transaction("txn-215", "2026-02-18", "AT&T", 85.00, "Utilities", "Phone bill")
    );
    double total = txns.stream().mapToDouble(Transaction::amount).sum();
    return new Statement("stmt-2026-02", "acc-1001", "2026-02-01", "2026-02-28", total, 0, txns);
  }
}
