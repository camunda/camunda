/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import io.camunda.analytics.lake.serving.sql.SqlText;
import java.util.Set;

/** Turns a {@link CohortSpec} into the boolean SQL expression identifying its "slow" cohort. */
public final class CohortPredicate {

  private static final Set<String> ALLOWED_OPERATORS = Set.of(">", "<", ">=", "<=", "=", "!=");

  private CohortPredicate() {}

  /**
   * @param allowedColumns every raw column a {@link CohortSpec.Threshold#measure()} is allowed to
   *     name (never interpolated unvalidated)
   */
  public static String toSql(final CohortSpec spec, final Set<String> allowedColumns) {
    if (spec instanceof final CohortSpec.Threshold threshold) {
      if (!allowedColumns.contains(threshold.measure())) {
        throw new IllegalArgumentException(
            "Unknown cohort measure '"
                + threshold.measure()
                + "'; known columns: "
                + allowedColumns);
      }
      if (!ALLOWED_OPERATORS.contains(threshold.op())) {
        throw new IllegalArgumentException(
            "Unsupported cohort operator '" + threshold.op() + "'; allowed: " + ALLOWED_OPERATORS);
      }
      return SqlText.identifier(threshold.measure())
          + " "
          + threshold.op()
          + " "
          + threshold.value();
    }
    if (spec instanceof final CohortSpec.WindowSplit windowSplit) {
      return "ended_at >= " + SqlText.timestamptzLiteral(windowSplit.at());
    }
    throw new IllegalArgumentException("Unknown cohort spec: " + spec);
  }
}
