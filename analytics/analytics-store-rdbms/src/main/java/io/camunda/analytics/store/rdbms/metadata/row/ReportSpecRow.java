/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata.row;

/**
 * Row model for {@code ANALYTICS_REPORT_SPEC}: the searchable scalar columns ({@code reportId}/
 * {@code name}) plus the full report as a JSON string. Like the dataset spec, a report is small,
 * config-like and read wholesale, so it is stored as an opaque document rather than normalized into
 * child tables.
 */
public final class ReportSpecRow {

  private long reportId;
  private String name;
  private String spec;

  public ReportSpecRow() {}

  public ReportSpecRow(final long reportId, final String name, final String spec) {
    this.reportId = reportId;
    this.name = name;
    this.spec = spec;
  }

  public long getReportId() {
    return reportId;
  }

  public void setReportId(final long reportId) {
    this.reportId = reportId;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getSpec() {
    return spec;
  }

  public void setSpec(final String spec) {
    this.spec = spec;
  }
}
