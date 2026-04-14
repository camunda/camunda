/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.importing.ReportingMetricsDto;
import io.camunda.optimize.dto.optimize.importing.ReportingMetricsMappings;
import io.camunda.optimize.service.db.schema.ScriptData;
import java.util.Map;
import java.util.Objects;

/** Shared script-data generation for reporting-metrics upserts. */
public final class ReportingMetricsWriterSupport {

  private ReportingMetricsWriterSupport() {}

  public static ScriptData buildScriptData(
      final ReportingMetricsDto doc, final ObjectMapper objectMapper) {
    final Map<String, Object> params = objectMapper.convertValue(doc, new TypeReference<>() {});
    params.values().removeIf(Objects::isNull);
    return new ScriptData(params, ReportingMetricsMappings.getUpdateScript());
  }
}
