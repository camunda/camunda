/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.Metrics;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Common test suite for {@link AbstractProcessInstancesArchiverJob} implementations. Concrete
 * subclasses supply the job under test via {@link #buildJob(List)} and stub the archiving mocks via
 * {@link #mockSuccessfulArchiving()}.
 */
@ExtendWith(MockitoExtension.class)
abstract class AbstractProcessInstancesArchiverJobTest {

  @Mock protected Archiver archiver;
  @Mock protected ListViewTemplate processInstanceTemplate;
  @Mock protected ArchiverRepository archiverRepository;

  protected MeterRegistry meterRegistry;
  protected Metrics metrics;

  @BeforeEach
  void setUpMetrics() {
    meterRegistry = new SimpleMeterRegistry();
    metrics = new Metrics(meterRegistry);
  }

  /** Creates the job under test for the given partition IDs. */
  protected abstract AbstractProcessInstancesArchiverJob buildJob(List<Integer> partitionIds);

  /** Stubs the mocks so that archiving a non-null batch completes successfully. */
  protected abstract void mockSuccessfulArchiving();

  private double gaugeValue(final int partitionId) {
    return meterRegistry
        .get(Metrics.GAUGE_NAME_TOTAL_PENDING_ARCHIVE_INSTANCES)
        .tag(Metrics.TAG_KEY_PARTITION, Integer.toString(partitionId))
        .gauge()
        .value();
  }

  @Test
  void gaugesRegisteredForEachPartition() {
    buildJob(List.of(1, 2));

    assertThat(gaugeValue(1)).isEqualTo(0.0);
    assertThat(gaugeValue(2)).isEqualTo(0.0);
  }

  @Test
  void archiveBatchUpdatesGaugeValues() {
    mockSuccessfulArchiving();
    final AbstractProcessInstancesArchiverJob job = buildJob(List.of(1, 2));

    final ArchiveBatch batch =
        new ArchiveBatch("2024-01-01", List.of("key1", "key2"), Map.of(1, 42L, 2, 17L));
    job.archiveBatch(batch).join();

    assertThat(gaugeValue(1)).isEqualTo(42.0);
    assertThat(gaugeValue(2)).isEqualTo(17.0);
  }

  @Test
  void archiveBatchWithNullReturnsZero() {
    final AbstractProcessInstancesArchiverJob job = buildJob(List.of(1));

    final Integer result = job.archiveBatch(null).join();

    assertThat(result).isEqualTo(0);
  }

  @Test
  void archiveBatchReturnsArchivedCount() {
    mockSuccessfulArchiving();
    final AbstractProcessInstancesArchiverJob job = buildJob(List.of(1));

    final ArchiveBatch batch =
        new ArchiveBatch("2024-01-01", List.of("key1", "key2"), Map.of(1, 2L));
    final Integer result = job.archiveBatch(batch).join();

    assertThat(result).isEqualTo(2);
  }

  @Test
  void archiveBatchWithNullResetsPreviouslyNonZeroPendingToZero() {
    mockSuccessfulArchiving();
    final AbstractProcessInstancesArchiverJob job = buildJob(List.of(1, 2));

    // first set pending counts > 0 via a real batch
    job.archiveBatch(
            new ArchiveBatch("2024-01-01", List.of("key1", "key2"), Map.of(1, 42L, 2, 17L)))
        .join();
    assertThat(gaugeValue(1)).isEqualTo(42.0);

    // null batch (nothing to archive) must reset all partitions to 0
    job.archiveBatch(null).join();

    assertThat(gaugeValue(1)).isEqualTo(0.0);
    assertThat(gaugeValue(2)).isEqualTo(0.0);
  }

  @Test
  void archiveBatchResetsPartitionMissingFromAggregationToZero() {
    mockSuccessfulArchiving();
    final AbstractProcessInstancesArchiverJob job = buildJob(List.of(1, 2));

    // batch 1: both partitions have pending
    job.archiveBatch(new ArchiveBatch("2024-01-01", List.of("key1"), Map.of(1, 42L, 2, 17L)))
        .join();
    assertThat(gaugeValue(2)).isEqualTo(17.0);

    // batch 2: partition 2 dropped to zero -> omitted from terms aggregation result
    job.archiveBatch(new ArchiveBatch("2024-01-02", List.of("key2"), Map.of(1, 5L))).join();

    assertThat(gaugeValue(1)).isEqualTo(5.0);
    assertThat(gaugeValue(2)).isEqualTo(0.0);
  }
}
