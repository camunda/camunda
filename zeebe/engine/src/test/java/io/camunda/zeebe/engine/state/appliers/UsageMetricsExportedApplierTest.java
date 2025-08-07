/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.engine.state.metrics.PersistedUsageMetrics.TIME_NOT_SET;
import static io.camunda.zeebe.util.HashUtil.getStringHashValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.metrics.PersistedUsageMetrics;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
class UsageMetricsExportedApplierTest {
  private static final long ASSIGNEE_HASH_1 = getStringHashValue("assignee1");
  private MutableProcessingState processingState;
  private MutableUsageMetricState usageMetricState;
  private UsageMetricsExportedApplier usageMetricsExportedApplier;

  @BeforeEach
  public void setup() {
    usageMetricState = processingState.getUsageMetricState();
    usageMetricsExportedApplier = new UsageMetricsExportedApplier(processingState);
  }

  @Test
  void shouldNotResetActiveBucketWhenResetTimeIsFromTime() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockUsageMetricsState = mock(MutableUsageMetricState.class);
    when(mockProcessingState.getUsageMetricState()).thenReturn(mockUsageMetricsState);
    when(mockUsageMetricsState.getActiveBucket())
        .thenReturn(new PersistedUsageMetrics().setFromTime(3L));

    // when
    new UsageMetricsExportedApplier(mockProcessingState)
        .applyState(1L, new UsageMetricRecord().setResetTime(3L));

    // then
    verify(mockUsageMetricsState).getActiveBucket();
    verify(mockUsageMetricsState, never()).getOrCreateActiveBucket();
    verify(mockUsageMetricsState, never()).resetActiveBucket(1L);
  }

  @Test
  void shouldUpdateActiveBucketTimeWhenNotInitialized() {
    // given
    usageMetricState.recordRPIMetric("tenant1");
    assertThat(usageMetricState.getActiveBucket().getFromTime()).isEqualTo(TIME_NOT_SET);
    assertThat(usageMetricState.getActiveBucket().getToTime()).isEqualTo(TIME_NOT_SET);
    assertThat(usageMetricState.getActiveBucket().getTenantRPIMap())
        .containsExactlyInAnyOrderEntriesOf(Map.of("tenant1", 1L));

    // when
    usageMetricsExportedApplier.applyState(1L, new UsageMetricRecord().setResetTime(2L));

    // then
    assertThat(usageMetricState.getActiveBucket().getFromTime()).isEqualTo(2L);
    assertThat(usageMetricState.getActiveBucket().getToTime()).isEqualTo(300002L);
    assertThat(usageMetricState.getActiveBucket().getTenantRPIMap())
        .containsExactlyInAnyOrderEntriesOf(Map.of("tenant1", 1L));
  }

  @Test
  void shouldResetActiveBucketRPI() {
    // given
    usageMetricState.resetActiveBucket(1L);
    usageMetricState.recordRPIMetric("tenant1");
    usageMetricState.recordRPIMetric("tenant1");
    final var bucket = usageMetricState.getActiveBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantRPIMap()).containsExactlyInAnyOrderEntriesOf(Map.of("tenant1", 2L));

    // when
    usageMetricsExportedApplier.applyState(1L, new UsageMetricRecord().setResetTime(2L));

    // then
    assertThat(usageMetricState.getActiveBucket().getFromTime()).isEqualTo(2L);
    assertThat(usageMetricState.getActiveBucket().getToTime()).isEqualTo(300002L);
    assertThat(usageMetricState.getActiveBucket().getTenantRPIMap()).isEmpty();
  }

  @Test
  void shouldResetActiveBucketEDI() {
    // given
    usageMetricState.resetActiveBucket(1L);
    usageMetricState.recordEDIMetric("tenant1");
    usageMetricState.recordEDIMetric("tenant1");
    final var bucket = usageMetricState.getActiveBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantEDIMap()).containsExactlyInAnyOrderEntriesOf(Map.of("tenant1", 2L));

    // when
    usageMetricsExportedApplier.applyState(1L, new UsageMetricRecord().setResetTime(2L));

    // then
    assertThat(usageMetricState.getActiveBucket().getFromTime()).isEqualTo(2L);
    assertThat(usageMetricState.getActiveBucket().getToTime()).isEqualTo(300002L);
    assertThat(usageMetricState.getActiveBucket().getTenantEDIMap()).isEmpty();
  }

  @Test
  void shouldResetActiveBucketTU() {
    // given
    usageMetricState.resetActiveBucket(1L);
    usageMetricState.recordTUMetric("tenant1", "assignee1");
    usageMetricState.recordTUMetric("tenant1", "assignee1");
    final var bucket = usageMetricState.getActiveBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantTUMap())
        .containsExactlyInAnyOrderEntriesOf(Map.of("tenant1", Set.of(ASSIGNEE_HASH_1)));

    // when
    usageMetricsExportedApplier.applyState(1L, new UsageMetricRecord().setResetTime(2L));

    // then
    assertThat(usageMetricState.getActiveBucket().getFromTime()).isEqualTo(2L);
    assertThat(usageMetricState.getActiveBucket().getToTime()).isEqualTo(300002L);
    assertThat(usageMetricState.getActiveBucket().getTenantTUMap()).isEmpty();
  }
}
