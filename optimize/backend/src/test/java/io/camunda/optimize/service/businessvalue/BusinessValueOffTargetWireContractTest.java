/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto.OffTargetEntryDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto.OffTargetStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pins the {@code offTarget} wire shape, which Web Modeler reads directly.
 *
 * <p>The assembly tests assert Java objects, so they keep passing if a {@code @JsonValue} id is
 * renamed or a null field starts being omitted — changes invisible in Java but breaking for the
 * consumer, which switches on the status string and treats a missing value as a measured miss.
 * These assertions are against the serialized JSON for that reason.
 */
class BusinessValueOffTargetWireContractTest {

  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  @ParameterizedTest(name = "{0} serializes as \"{1}\"")
  @CsvSource({
    "OFF_TARGET, offTarget",
    "NO_COMPLETED_INSTANCES, noCompletedInstances",
    "NOT_APPLICABLE, notApplicable",
    "NOT_MEASURED, notMeasured"
  })
  void shouldSerializeEveryStatusAsItsWireId(final OffTargetStatus status, final String wireId)
      throws Exception {
    final String json = mapper.writeValueAsString(entry(status, 72.0, 10.0, "under"));

    assertThat(json).contains("\"status\":\"" + wireId + "\"");
    assertThat(json).doesNotContain(status.name());
  }

  @Test
  void shouldEmitNullValueGapAndComparisonForAnEntryWithNoMeasurement() throws Exception {
    // The consumer distinguishes "no gap to show" from "a gap of zero". Omitting these fields, or
    // defaulting them to 0, would render a fabricated "0% over target" against an unmeasured KPI.
    final String json =
        mapper.writeValueAsString(entry(OffTargetStatus.NO_COMPLETED_INSTANCES, null, null, null));

    assertThat(json).contains("\"value\":null");
    assertThat(json).contains("\"gapPct\":null");
    assertThat(json).contains("\"comparison\":null");
    // The target is known even when nothing measured it, so it is never null.
    assertThat(json).contains("\"target\":80.0");
  }

  @Test
  void shouldRoundTripAnUnmeasuredEntry() throws Exception {
    final OffTargetEntryDto original = entry(OffTargetStatus.NOT_APPLICABLE, null, null, null);

    final OffTargetEntryDto roundTripped =
        mapper.readValue(mapper.writeValueAsString(original), OffTargetEntryDto.class);

    assertThat(roundTripped).isEqualTo(original);
    assertThat(roundTripped.getStatus()).isEqualTo(OffTargetStatus.NOT_APPLICABLE);
    assertThat(roundTripped.getValue()).isNull();
    assertThat(roundTripped.getGapPct()).isNull();
  }

  private static OffTargetEntryDto entry(
      final OffTargetStatus status,
      final Double value,
      final Double gapPct,
      final String comparison) {
    return new OffTargetEntryDto(
        "<default>",
        "invoice-automation",
        "Invoice Automation",
        Kpi.AUTOMATION_RATE.getId(),
        status,
        value,
        80.0,
        "PERCENT",
        gapPct,
        comparison);
  }
}
