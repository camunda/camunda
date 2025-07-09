/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.metrics.DbUsageMetricState;
import io.camunda.zeebe.engine.state.metrics.PersistedUsageMetrics;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
class UsageMetricsExportedApplierTest {

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private MutableUsageMetricState usageMetricState;
  private UsageMetricsExportedApplier usageMetricsExportedApplier;

  @BeforeEach
  public void setup() {
    usageMetricState =
        new DbUsageMetricState(zeebeDb, transactionContext, Duration.ofMinutes(5), true);
    usageMetricsExportedApplier = new UsageMetricsExportedApplier(usageMetricState);
  }

  @Test
  void shouldResetActiveBucket() {
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
  void shouldNotResetActiveBucketWhenResetTimeIsFromTime() {
    // given
    final var mockUsageMetricsState = mock(MutableUsageMetricState.class);
    when(mockUsageMetricsState.getActiveBucket())
        .thenReturn(new PersistedUsageMetrics().setFromTime(3L));

    // when
    new UsageMetricsExportedApplier(mockUsageMetricsState)
        .applyState(1L, new UsageMetricRecord().setResetTime(3L));

    // then
    verify(mockUsageMetricsState, never()).resetActiveBucket(1L);
  }
}
