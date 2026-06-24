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

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
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
    assertThat(usageMetricState.getActiveBucket().getToTime()).isEqualTo(TIME_NOT_SET);
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
    assertThat(usageMetricState.getActiveBucket().getToTime()).isEqualTo(TIME_NOT_SET);
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
    assertThat(usageMetricState.getActiveBucket().getToTime()).isEqualTo(TIME_NOT_SET);
    assertThat(usageMetricState.getActiveBucket().getTenantTUMap()).isEmpty();
  }

  @Test
  void shouldNotResetBucketWithOtherEventTypes() {
    usageMetricState.resetActiveBucket(1L);
    usageMetricState.recordRPIMetric("tenant1");
    usageMetricState.recordRPIMetric("tenant1");
    final var bucket = usageMetricState.getActiveBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantRPIMap()).containsExactlyInAnyOrderEntriesOf(Map.of("tenant1", 2L));

    // when
    usageMetricsExportedApplier.applyState(
        1L, new UsageMetricRecord().setEventType(EventType.RPI).setResetTime(2L));

    // then
    assertThat(usageMetricState.getActiveBucket().getFromTime()).isEqualTo(1L);
    assertThat(usageMetricState.getActiveBucket().getToTime()).isEqualTo(TIME_NOT_SET);
    assertThat(bucket.getTenantRPIMap()).containsExactlyInAnyOrderEntriesOf(Map.of("tenant1", 2L));
  }
}
