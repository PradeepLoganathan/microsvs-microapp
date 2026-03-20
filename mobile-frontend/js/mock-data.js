// ============================================
// Mock Data — mirrors real backend seed data
// ============================================

window.MOCK_DATA = {
  // --- Account ---
  account: {
    id: 'acc-1001',
    name: 'Demo User',
    balance: 12450.00,
    type: 'Checking',
  },

  // --- Home Page Recent Transactions ---
  recentTransactions: [
    { description: 'Salary Deposit', amount: 5200.00, date: 'Feb 15, 2026', type: 'credit' },
    { description: 'Grocery Store', amount: 87.45, date: 'Feb 14, 2026', type: 'debit' },
    { description: 'Electric Bill', amount: 142.00, date: 'Feb 12, 2026', type: 'debit' },
    { description: 'Freelance Payment', amount: 750.00, date: 'Feb 10, 2026', type: 'credit' },
    { description: 'Restaurant', amount: 62.30, date: 'Feb 9, 2026', type: 'debit' },
  ],

  // --- Statements (from MockDataProvider.java) ---
  statements: [
    {
      statementId: 'stmt-2025-12',
      accountId: 'acc-1001',
      periodStart: '2025-12-01',
      periodEnd: '2025-12-31',
      totalDebits: 1784.74,
      transactionCount: 15,
      transactions: [
        { id: 'txn-001', date: '2025-12-02', merchant: 'Delta Airlines', amount: 487.50, category: 'Travel', description: 'Round trip ATL-LAX' },
        { id: 'txn-002', date: '2025-12-02', merchant: 'Marriott Hotels', amount: 312.00, category: 'Travel', description: '3 nights Los Angeles' },
        { id: 'txn-003', date: '2025-12-03', merchant: 'Uber', amount: 42.30, category: 'Travel', description: 'Airport transfer LAX' },
        { id: 'txn-004', date: '2025-12-05', merchant: 'Whole Foods', amount: 67.80, category: 'Groceries', description: 'Weekly groceries' },
        { id: 'txn-005', date: '2025-12-07', merchant: 'Shell Gas Station', amount: 55.20, category: 'Utilities', description: 'Fuel' },
        { id: 'txn-006', date: '2025-12-10', merchant: 'Airbnb', amount: 198.00, category: 'Travel', description: 'Weekend cabin rental' },
        { id: 'txn-007', date: '2025-12-12', merchant: 'Duty Free Shop', amount: 89.50, category: 'Travel', description: 'Airport shopping' },
        { id: 'txn-008', date: '2025-12-14', merchant: 'Netflix', amount: 15.99, category: 'Entertainment', description: 'Monthly subscription' },
        { id: 'txn-009', date: '2025-12-15', merchant: 'United Airlines', amount: 352.00, category: 'Travel', description: 'One-way SFO-ORD' },
        { id: 'txn-010', date: '2025-12-18', merchant: 'Hilton Hotels', amount: 245.00, category: 'Travel', description: '2 nights Chicago' },
        { id: 'txn-011', date: '2025-12-20', merchant: 'Uber Eats', amount: 34.50, category: 'Dining', description: 'Dinner delivery' },
        { id: 'txn-012', date: '2025-12-22', merchant: "Trader Joe's", amount: 52.30, category: 'Groceries', description: 'Holiday groceries' },
        { id: 'txn-013', date: '2025-12-24', merchant: 'Amazon', amount: 125.00, category: 'Shopping', description: 'Holiday gifts' },
        { id: 'txn-014', date: '2025-12-28', merchant: 'Lyft', amount: 28.75, category: 'Travel', description: 'City ride' },
        { id: 'txn-015', date: '2025-12-30', merchant: 'AT&T', amount: 85.00, category: 'Utilities', description: 'Phone bill' },
      ],
    },
    {
      statementId: 'stmt-2026-01',
      accountId: 'acc-1001',
      periodStart: '2026-01-01',
      periodEnd: '2026-01-31',
      totalDebits: 1188.33,
      transactionCount: 15,
      transactions: [
        { id: 'txn-101', date: '2026-01-02', merchant: 'CVS Pharmacy', amount: 67.50, category: 'Health', description: 'Prescription medications' },
        { id: 'txn-102', date: '2026-01-03', merchant: 'Planet Fitness', amount: 49.99, category: 'Health', description: 'Monthly gym membership' },
        { id: 'txn-103', date: '2026-01-05', merchant: 'Whole Foods', amount: 89.20, category: 'Groceries', description: 'Organic produce' },
        { id: 'txn-104', date: '2026-01-07', merchant: 'Dr. Smith Office', amount: 150.00, category: 'Health', description: 'Annual checkup copay' },
        { id: 'txn-105', date: '2026-01-08', merchant: 'GNC Nutrition', amount: 78.40, category: 'Health', description: 'Vitamins and supplements' },
        { id: 'txn-106', date: '2026-01-10', merchant: 'Kroger', amount: 62.15, category: 'Groceries', description: 'Weekly groceries' },
        { id: 'txn-107', date: '2026-01-12', merchant: 'Walgreens', amount: 35.80, category: 'Health', description: 'OTC medicines' },
        { id: 'txn-108', date: '2026-01-14', merchant: 'Spotify', amount: 10.99, category: 'Entertainment', description: 'Music subscription' },
        { id: 'txn-109', date: '2026-01-15', merchant: 'Dental Care Plus', amount: 120.00, category: 'Health', description: 'Dental cleaning' },
        { id: 'txn-110', date: '2026-01-18', merchant: "Trader Joe's", amount: 71.30, category: 'Groceries', description: 'Health foods' },
        { id: 'txn-111', date: '2026-01-20', merchant: 'Vision Center', amount: 95.00, category: 'Health', description: 'Eye exam' },
        { id: 'txn-112', date: '2026-01-22', merchant: 'Duke Energy', amount: 142.00, category: 'Utilities', description: 'Electric bill' },
        { id: 'txn-113', date: '2026-01-25', merchant: 'Yoga Studio', amount: 80.00, category: 'Health', description: 'Monthly yoga classes' },
        { id: 'txn-114', date: '2026-01-27', merchant: 'Chipotle', amount: 14.50, category: 'Dining', description: 'Lunch' },
        { id: 'txn-115', date: '2026-01-30', merchant: 'AT&T', amount: 85.00, category: 'Utilities', description: 'Phone bill' },
      ],
    },
    {
      statementId: 'stmt-2026-02',
      accountId: 'acc-1001',
      periodStart: '2026-02-01',
      periodEnd: '2026-02-28',
      totalDebits: 1058.68,
      transactionCount: 15,
      transactions: [
        { id: 'txn-201', date: '2026-02-01', merchant: 'Duke Energy', amount: 138.00, category: 'Utilities', description: 'Electric bill' },
        { id: 'txn-202', date: '2026-02-02', merchant: 'Whole Foods', amount: 94.50, category: 'Groceries', description: 'Weekly groceries' },
        { id: 'txn-203', date: '2026-02-03', merchant: 'AMC Theaters', amount: 32.00, category: 'Entertainment', description: 'Movie night' },
        { id: 'txn-204', date: '2026-02-05', merchant: 'Olive Garden', amount: 68.40, category: 'Dining', description: 'Dinner for two' },
        { id: 'txn-205', date: '2026-02-07', merchant: 'Costco', amount: 185.30, category: 'Groceries', description: 'Bulk shopping' },
        { id: 'txn-206', date: '2026-02-08', merchant: 'Comcast', amount: 79.99, category: 'Utilities', description: 'Internet bill' },
        { id: 'txn-207', date: '2026-02-10', merchant: 'Starbucks', amount: 24.50, category: 'Dining', description: 'Coffee runs' },
        { id: 'txn-208', date: '2026-02-11', merchant: 'Netflix', amount: 15.99, category: 'Entertainment', description: 'Monthly subscription' },
        { id: 'txn-209', date: '2026-02-12', merchant: 'Water Authority', amount: 45.00, category: 'Utilities', description: 'Water bill' },
        { id: 'txn-210', date: '2026-02-14', merchant: "Ruth's Chris", amount: 142.00, category: 'Dining', description: "Valentine's dinner" },
        { id: 'txn-211', date: '2026-02-15', merchant: 'Kroger', amount: 58.70, category: 'Groceries', description: 'Weekly groceries' },
        { id: 'txn-212', date: '2026-02-16', merchant: 'Hulu', amount: 17.99, category: 'Entertainment', description: 'Streaming subscription' },
        { id: 'txn-213', date: '2026-02-17', merchant: 'Shell Gas Station', amount: 48.50, category: 'Utilities', description: 'Fuel' },
        { id: 'txn-214', date: '2026-02-18', merchant: 'Panera Bread', amount: 22.30, category: 'Dining', description: 'Lunch' },
        { id: 'txn-215', date: '2026-02-18', merchant: 'AT&T', amount: 85.00, category: 'Utilities', description: 'Phone bill' },
      ],
    },
  ],

  // --- Analysis Summary (stmt-2025-12, travel-heavy) ---
  analysis: {
    accountId: 'acc-1001',
    statementId: 'stmt-2025-12',
    totalSpend: 1784.74,
    categories: [
      { category: 'Travel', total: 1126.05, count: 7, percentage: 63.11 },
      { category: 'Utilities', total: 140.20, count: 3, percentage: 7.86 },
      { category: 'Shopping', total: 125.00, count: 1, percentage: 7.01 },
      { category: 'Groceries', total: 120.10, count: 2, percentage: 6.73 },
      { category: 'Dining', total: 48.99, count: 2, percentage: 2.75 },
      { category: 'Entertainment', total: 15.99, count: 1, percentage: 0.90 },
    ],
    topMerchants: [
      { merchant: 'Delta Airlines', total: 487.50, count: 1 },
      { merchant: 'United Airlines', total: 352.00, count: 1 },
      { merchant: 'Marriott Hotels', total: 312.00, count: 1 },
      { merchant: 'Hilton Hotels', total: 245.00, count: 1 },
      { merchant: 'Airbnb', total: 198.00, count: 1 },
    ],
    insights: [
      { type: 'top_category', message: 'Travel is your highest spending category at 63.11% of total spend ($1,126.05)' },
      { type: 'travel_alert', message: 'Travel spending exceeds 30% of your total — consider a travel rewards card for maximum benefits' },
      { type: 'recurring', message: '2 recurring payment(s) detected — automated bill pay can help avoid missed payments' },
    ],
  },

  // --- Recommendations (travel-heavy month) ---
  recommendations: [
    {
      productId: 'travel_rewards_card',
      productName: 'Travel Rewards Platinum Card',
      reason: 'Your travel spending is 63.11% of total — earn 3x points on every trip',
    },
    {
      productId: 'bill_pay_assist',
      productName: 'SmartBill Pay Assistant',
      reason: 'Recurring payments detected — automate bills and never miss a due date',
    },
  ],

  // --- Products (from ProductEndpoint seed) ---
  products: [
    { productId: 'travel_rewards_card', productName: 'Travel Rewards Platinum Card', category: 'Credit Card', description: 'Earn 3x points on travel purchases, no foreign transaction fees, complimentary lounge access', eligibility: 'Customers with high travel spending' },
    { productId: 'health_insurance_basic', productName: 'HealthGuard Basic Plan', category: 'Insurance', description: 'Comprehensive coverage for medical, dental, and vision with low copays and preventive care', eligibility: 'Customers with regular health-related spending' },
    { productId: 'cashback_everyday_card', productName: 'Cashback Everyday Card', category: 'Credit Card', description: '2% cashback on groceries and utilities, 1% on everything else, no annual fee', eligibility: 'Customers with high grocery and utility spending' },
    { productId: 'bill_pay_assist', productName: 'SmartBill Pay Assistant', category: 'Banking Service', description: 'Automated bill payments with smart scheduling, payment reminders, and late fee protection', eligibility: 'Customers with recurring utility and subscription payments' },
  ],

  // --- Default Manifest ---
  defaultManifest: {
    manifestVersion: '2026-02-20T00:00:00Z',
    channel: 'demo',
    microfrontends: [
      { name: 'statement-details', version: '1.0.0', remoteEntry: '/js/microapps/statement-details.js', exposedModule: './Module', elementTag: 'mf-statement-details' },
      { name: 'statement-analysis', version: '1.0.0', remoteEntry: '/js/microapps/statement-analysis.js', exposedModule: './Module', elementTag: 'mf-statement-analysis' },
      { name: 'recommendations', version: '1.0.0', remoteEntry: '/js/microapps/recommendations.js', exposedModule: './Module', elementTag: 'mf-recommendations' },
    ],
  },
};
