/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import static io.camunda.zeebe.engine.state.jobmetrics.DbJobMetricsStore.META_COUNTER;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** What is specific to the RocksDB store: the string dictionary of the metrics' keys. */
@ExtendWith(ProcessingStateExtension.class)
final class DbJobMetricsStoreTest {

  private MutableProcessingState processingState;
  private MutableJobMetricsState state;

  @BeforeEach
  void setUp() {
    state = processingState.getJobMetricsState();
  }

  @Test
  void shouldIncrementCounterForEachNewString() {
    // given / when
    state.incrementMetric(
        new JobRecord().setType("jobType1").setTenantId("tenant1").setWorker("worker1"),
        JobMetricsExportState.CREATED);

    // then - 3 new strings created, counter should be 3
    assertThat(state.getMetadata(META_COUNTER)).isEqualTo(3L);
  }

  @Test
  void shouldNotIncrementCounterForReusedStrings() {
    // given
    state.incrementMetric(
        new JobRecord().setType("jobType1").setTenantId("tenant1").setWorker("worker1"),
        JobMetricsExportState.CREATED);
    final long counterAfterFirst = state.getMetadata(META_COUNTER);

    // when - reuse all strings
    state.incrementMetric(
        new JobRecord().setType("jobType1").setTenantId("tenant1").setWorker("worker1"),
        JobMetricsExportState.COMPLETED);

    // then
    assertThat(state.getMetadata(META_COUNTER)).isEqualTo(counterAfterFirst);
  }

  @Test
  void shouldResetCounterOnCleanUp() {
    // given
    state.incrementMetric(
        new JobRecord().setType("jobType1").setTenantId("tenant1").setWorker("worker1"),
        JobMetricsExportState.CREATED);

    // when
    state.cleanUp();

    // then
    assertThat(state.getMetadata(META_COUNTER)).isZero();
  }

  @Test
  void shouldEncodeStringsInTheOrderOfTheirCreation() {
    // given
    state.incrementMetric(
        new JobRecord().setType("first").setTenantId("second").setWorker("third"),
        JobMetricsExportState.CREATED);

    // when
    final List<String> encodedStrings = state.getEncodedStrings();

    // then
    assertThat(encodedStrings).containsExactly("first", "second", "third");
  }
}
