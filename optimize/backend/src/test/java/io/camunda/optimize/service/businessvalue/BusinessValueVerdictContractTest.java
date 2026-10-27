/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BusinessValueVerdictContractTest {

  private static final String FIXTURE_RESOURCE = "/businessvalue/verdict-cases.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  // 1e-12 absolute tolerance: covers last-ULP drift between Java double arithmetic and the JSON
  // literal, well below any precision that would matter for a KPI gap percentage (a 1e-10 %
  // difference on any real metric is invisible to a reader).
  private static final double GAP_PCT_TOLERANCE = 1e-12;

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtureCases")
  void shouldMatchFixtureCase(
      final String label, final Map<String, Object> input, final Map<String, Object> expect) {
    // given
    final Kpi kpi = Kpi.fromId((String) input.get("kpi"));
    final Double value = asDouble(input.get("value"));
    final Double target = asDouble(input.get("target"));
    final Direction direction = Direction.valueOf((String) input.get("direction"));

    // when
    final Verdict actual = BusinessValueVerdict.verdict(kpi, value, target, direction);

    // then
    assertThat(actual.kpi()).isEqualTo(kpi);
    assertThat(actual.value()).isEqualTo(asDouble(expect.get("value")));
    assertThat(actual.target()).isEqualTo(asDouble(expect.get("target")));
    assertThat(actual.met()).isEqualTo(expect.get("met"));
    final Double expectedGap = asDouble(expect.get("gapPct"));
    if (expectedGap == null) {
      assertThat(actual.gapPct()).isNull();
    } else {
      assertThat(actual.gapPct()).isCloseTo(expectedGap, within(GAP_PCT_TOLERANCE));
    }
    assertThat(actual.direction()).isEqualTo(expect.get("direction"));
  }

  private static Stream<org.junit.jupiter.params.provider.Arguments> fixtureCases()
      throws IOException {
    try (InputStream in =
        BusinessValueVerdictContractTest.class.getResourceAsStream(FIXTURE_RESOURCE)) {
      assertThat(in).as("fixture " + FIXTURE_RESOURCE + " must be on the classpath").isNotNull();
      final List<Map<String, Object>> rows =
          MAPPER.readValue(in, new TypeReference<List<Map<String, Object>>>() {});
      return rows.stream()
          .map(
              row ->
                  org.junit.jupiter.params.provider.Arguments.of(
                      row.get("case"), row.get("input"), row.get("expect")));
    }
  }

  private static Double asDouble(final Object raw) {
    if (raw == null) {
      return null;
    }
    return ((Number) raw).doubleValue();
  }
}
