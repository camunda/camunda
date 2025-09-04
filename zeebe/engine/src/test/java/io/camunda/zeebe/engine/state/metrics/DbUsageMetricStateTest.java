/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.metrics;

import static io.camunda.zeebe.util.HashUtil.getStringHashValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class DbUsageMetricStateTest {

  private static final long ASSIGNEE_HASH_1 = getStringHashValue("assignee1");
  private static final long ASSIGNEE_HASH_2 = getStringHashValue("assignee2");
  private static final long ASSIGNEE_HASH_3 = getStringHashValue("assignee3");
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private InstantSource mockClock;
  private MutableUsageMetricState state;

  @BeforeEach
  void beforeEach() {
    mockClock = mock(InstantSource.class);
    state = new DbUsageMetricState(zeebeDb, transactionContext, Duration.ofSeconds(1));
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
    final var actual = state.getOrCreateActiveBucket();
    assertThat(actual.getFromTime()).isEqualTo(1L);
    assertThat(actual.getToTime()).isEqualTo(-1L);
    assertThat(actual.getTenantRPIMap()).isEmpty();
    assertThat(actual.getTenantEDIMap()).isEmpty();
    assertThat(actual.getTenantTUMap()).isEmpty();
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
    final var actual = state.getOrCreateActiveBucket();
    assertThat(actual.getFromTime()).isEqualTo(1L);
    assertThat(actual.getToTime()).isEqualTo(-1L);
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
    final var actual = state.getOrCreateActiveBucket();
    assertThat(actual.getFromTime()).isEqualTo(1L);
    assertThat(actual.getToTime()).isEqualTo(-1L);
    assertThat(actual.getTenantEDIMap())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 2L, "tenant1", 3L, "tenant2", 1L));
  }

  @Test
  public void shouldRecordTUMetrics() {
    // given
    final var eventTime = InstantSource.system().millis();
    when(mockClock.millis()).thenReturn(eventTime);
    state.resetActiveBucket(1L);

    // when
    state.recordTUMetric(TenantOwned.DEFAULT_TENANT_IDENTIFIER, "assignee1");
    state.recordTUMetric(TenantOwned.DEFAULT_TENANT_IDENTIFIER, "assignee1");
    state.recordTUMetric(TenantOwned.DEFAULT_TENANT_IDENTIFIER, "assignee2");
    state.recordTUMetric("tenant1", "assignee1");
    state.recordTUMetric("tenant1", "assignee2");
    state.recordTUMetric("tenant1", "assignee3");
    state.recordTUMetric("tenant2", "assignee1");

    // then
    final var actual = state.getOrCreateActiveBucket();
    assertThat(actual.getFromTime()).isEqualTo(1L);
    assertThat(actual.getToTime()).isEqualTo(-1L);
    assertThat(actual.getTenantTUMap())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                TenantOwned.DEFAULT_TENANT_IDENTIFIER,
                Set.of(ASSIGNEE_HASH_1, ASSIGNEE_HASH_2),
                "tenant1",
                Set.of(ASSIGNEE_HASH_1, ASSIGNEE_HASH_2, ASSIGNEE_HASH_3),
                "tenant2",
                Set.of(ASSIGNEE_HASH_1)));
  }
}
