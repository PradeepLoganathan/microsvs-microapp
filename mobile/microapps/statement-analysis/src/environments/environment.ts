export const environment = {
  production: false,
  version: 1,
  apiBaseUrl: 'http://localhost:8083',
  statementApiUrl: 'http://localhost:8082',
  // Curated demo statement — analysed when the active account has it; otherwise
  // the account's latest statement is used.
  preferredStatementId: 'stmt-2025-12',
};
