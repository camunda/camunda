/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.metrics;

import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class DbUsageMetricStateTest {
  private MutableProcessingState processingState;
  private MutableUsageMetricState state;

  @BeforeEach
  void beforeEach() {
    state = processingState.getUsageMetricState();
  }

  @Test
  public void shouldCreateRPIMetricProcessIncident() {
    // given
    final var eventTime = InstantSource.system().millis();

    // when
    state.createRPIMetric(eventTime, 123L, TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    final var actual = state.getTenantIdPIsMapByEventTime(eventTime);
    assertThat(actual)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER, List.of(123L)));
  }

  @Test
  public void shouldGetTenantIdPIsMapByEventTime() {
    // given
    final var eventTime1 = InstantSource.system().millis();
    final var eventTime2 =
        InstantSource.offset(InstantSource.system(), Duration.ofSeconds(10)).millis();
    state.createRPIMetric(eventTime1, 123L, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    state.createRPIMetric(eventTime2, 10L, "tenant1");
    state.createRPIMetric(eventTime2, 11L, "tenant1");
    state.createRPIMetric(eventTime2, 12L, "tenant2");

    // when
    final var actual1 = state.getTenantIdPIsMapByEventTime(eventTime1);
    final var actual2 = state.getTenantIdPIsMapByEventTime(eventTime2);

    // then
    assertThat(actual1)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER, List.of(123L)));
    assertThat(actual2)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("tenant1", List.of(10L, 11L), "tenant2", List.of(12L)));
  }

  @Test
  public void shouldDeleteByEventTime() {
    // given
    final var eventTime1 = InstantSource.system().millis();
    final var eventTime2 =
        InstantSource.offset(InstantSource.system(), Duration.ofSeconds(10)).millis();
    state.createRPIMetric(eventTime1, 123L, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    state.createRPIMetric(eventTime2, 10L, "tenant1");
    state.createRPIMetric(eventTime2, 11L, "tenant1");
    state.createRPIMetric(eventTime2, 12L, "tenant2");
    assertThat(state.getTenantIdPIsMapByEventTime(eventTime1))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER, List.of(123L)));
    assertThat(state.getTenantIdPIsMapByEventTime(eventTime2))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("tenant1", List.of(10L, 11L), "tenant2", List.of(12L)));

    // when
    state.deleteByEventTime(eventTime2);

    // then
    assertThat(state.getTenantIdPIsMapByEventTime(eventTime1))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER, List.of(123L)));
    assertThat(state.getTenantIdPIsMapByEventTime(eventTime2)).isEmpty();
  }
}
