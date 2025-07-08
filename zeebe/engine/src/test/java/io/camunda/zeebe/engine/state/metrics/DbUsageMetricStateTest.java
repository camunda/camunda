/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.metrics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class DbUsageMetricStateTest {

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private InstantSource mockClock;
  private MutableUsageMetricState state;

  @BeforeEach
  void beforeEach() {
    mockClock = mock(InstantSource.class);
    state = new DbUsageMetricState(zeebeDb, transactionContext, Duration.ofSeconds(1), true);
    state.resetActiveBucket(1L);
  }

  @Test
  public void shouldResetActiveBucket() {
    // given
    final var eventTime = InstantSource.system().millis();
    when(mockClock.millis()).thenReturn(eventTime);

    // when
    state.resetActiveBucket(1L);

    // then
    final var actual = state.getActiveBucket();
    assertThat(actual.getFromTime()).isEqualTo(1L);
    assertThat(actual.getToTime()).isEqualTo(1001L);
    assertThat(actual.getTenantRPIMap()).isEmpty();
  }

  @Test
  public void shouldRecordRPIMetrics() {
    // given
    final var eventTime = InstantSource.system().millis();
    when(mockClock.millis()).thenReturn(eventTime);
    state.resetActiveBucket(1L);

    // when
    state.recordRPIMetric(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    state.recordRPIMetric(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    state.recordRPIMetric("tenant1");
    state.recordRPIMetric("tenant1");
    state.recordRPIMetric("tenant1");
    state.recordRPIMetric("tenant2");

    // then
    final var actual = state.getActiveBucket();
    assertThat(actual.getFromTime()).isEqualTo(1L);
    assertThat(actual.getToTime()).isEqualTo(1001L);
    assertThat(actual.getTenantRPIMap())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 2L, "tenant1", 3L, "tenant2", 1L));
  }

  @Test
  public void shouldRecordEDIMetrics() {
    // given
    final var eventTime = InstantSource.system().millis();
    when(mockClock.millis()).thenReturn(eventTime);
    state.resetActiveBucket(1L);

    // when
    state.recordEDIMetric(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    state.recordEDIMetric(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    state.recordEDIMetric("tenant1");
    state.recordEDIMetric("tenant1");
    state.recordEDIMetric("tenant1");
    state.recordEDIMetric("tenant2");

    // then
    final var actual = state.getActiveBucket();
    assertThat(actual.getFromTime()).isEqualTo(1L);
    assertThat(actual.getToTime()).isEqualTo(1001L);
    assertThat(actual.getTenantEDIMap())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 2L, "tenant1", 3L, "tenant2", 1L));
  }
}
