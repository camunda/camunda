/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.analytics.report.ReportDefinition;

/**
 * Serializes a {@link ReportDefinition} to/from the JSON stored in {@code ANALYTICS_REPORT_SPEC.
 * SPEC}. The report's sources, group-by, combination and filters are Jackson records/enums/lists,
 * so a plain {@link ObjectMapper} round-trips them — the RDBMS backend's transform of the neutral
 * report spec (a document backend serializes the same model through its own client mapper).
 */
final class ReportSpecJson {

  private final ObjectMapper objectMapper = new ObjectMapper();

  String toJson(final ReportDefinition report) {
    try {
      return objectMapper.writeValueAsString(report);
    } catch (final Exception e) {
      throw new IllegalStateException("failed to serialize report spec " + report.reportId(), e);
    }
  }

  ReportDefinition fromJson(final String json) {
    try {
      return objectMapper.readValue(json, ReportDefinition.class);
    } catch (final Exception e) {
      throw new IllegalStateException("failed to deserialize report spec", e);
    }
  }
}
