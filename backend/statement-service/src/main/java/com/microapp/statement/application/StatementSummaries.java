package com.microapp.statement.application;

import com.microapp.statement.domain.StatementSummary;
import java.util.Collection;

public record StatementSummaries(Collection<StatementSummary> statements) {}
