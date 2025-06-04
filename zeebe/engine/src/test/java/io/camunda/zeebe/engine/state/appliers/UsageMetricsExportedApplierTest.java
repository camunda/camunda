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

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
class UsageMetricsExportedApplierTest {

  private MutableProcessingState processingState;
  private MutableUsageMetricState usageMetricState;
  private UsageMetricsExportedApplier usageMetricsExportedApplier;

  @BeforeEach
  public void setup() {
    usageMetricState = processingState.getUsageMetricState();
    usageMetricsExportedApplier = new UsageMetricsExportedApplier(processingState);
  }

  @Test
  void shouldResetRollingBucket() {
    // given
    usageMetricState.resetRollingBucket(1L);
    usageMetricState.recordRPIMetric("tenant1");
    usageMetricState.recordRPIMetric("tenant1");
    final var bucket = usageMetricState.getRollingBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantRPIMap()).containsExactlyInAnyOrderEntriesOf(Map.of("tenant1", 2L));

    // when
    usageMetricsExportedApplier.applyState(1L, new UsageMetricRecord().setResetTime(2L));

    // then
    assertThat(usageMetricState.getRollingBucket().getFromTime()).isEqualTo(2L);
    assertThat(usageMetricState.getRollingBucket().getToTime()).isEqualTo(300002L);
    assertThat(usageMetricState.getRollingBucket().getTenantRPIMap()).isEmpty();
  }
}
