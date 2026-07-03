/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

import java.util.List;

/** The result of a {@link ReportQuery}: the finalized {@link ReportRow}s. */
public record ReportResult(List<ReportRow> rows) {

  public ReportResult {
    rows = List.copyOf(rows);
  }
}
