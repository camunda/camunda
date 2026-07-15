/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.query.ReportRow;
import io.camunda.analytics.sketch.QuantileAggregateFunction;
import io.camunda.analytics.sketch.QuantileResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code QuantileResultTest} (analytics-model) pins the {@code @JsonIgnore} contract under Jackson
 * 2 ({@code com.fasterxml.jackson.databind.ObjectMapper}), but the dashboard actually serves report
 * rows through Spring Boot's own converter — and this webapp's Spring Boot version wires up Jackson
 * 3 ({@code tools.jackson.databind}), a different major version with its own annotation
 * introspection. A contract proven under one Jackson major version is not proof it holds under the
 * other, so this test pins the actual runtime the dashboard responds with: a {@link ReportRow} (the
 * type {@code AnalyticsController}'s compare-report endpoint returns as-is) whose measures carry a
 * sketch-backed {@link QuantileResult} must never leak the sketch into JSON.
 */
final class QuantileResultJackson3SerializationTest {

  @Test
  void shouldNotSerializeTheSketchThroughJackson3() {
    // given a report row whose measures carry a sketch-backed QuantileResult — the same shape
    // AnalyticsController serializes as-is (ComparedReportResult/ReportResult ride raw ReportRows)
    final QuantileAggregateFunction<Double> quantileFn =
        new QuantileAggregateFunction<>(Double::doubleValue);
    KllDoublesSketch sketch = quantileFn.createAccumulator();
    for (int i = 1; i <= 10; i++) {
      sketch = quantileFn.add((double) i, sketch);
    }
    final QuantileResult result = quantileFn.getResult(sketch);
    assertThat(result.sketch()).isNotNull(); // sanity: this is the sketch-carrying shape

    final Map<String, Object> measures = new LinkedHashMap<>();
    measures.put("percentiles", result);
    final ReportRow row = new ReportRow(Map.of("bpmnProcessId", "claim-process"), 0L, measures);

    // when serialized through the actual tools.jackson ObjectMapper Spring Boot 4 wires up here
    final String json = new ObjectMapper().writeValueAsString(row);

    // then the sketch never appears, even under this different Jackson major version
    assertThat(json).doesNotContain("sketch");
    assertThat(json).contains("\"percentiles\"").contains("\"count\"");
  }
}
