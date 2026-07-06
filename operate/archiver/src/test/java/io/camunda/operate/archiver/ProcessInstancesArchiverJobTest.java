/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.camunda.operate.Metrics;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessInstancesArchiverJobTest {

  @Mock private Archiver archiver;
  @Mock private ListViewTemplate processInstanceTemplate;
  @Mock private ArchiverRepository archiverRepository;

  private ProcessInstancesArchiverJob buildJob(
      final List<Integer> partitionIds, final Metrics metrics) {
    return new ProcessInstancesArchiverJob(
        archiver, partitionIds, processInstanceTemplate, List.of(), metrics, archiverRepository);
  }

  @Test
  void gaugesRegisteredForEachPartition() {
    final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    final Metrics metrics = new Metrics(meterRegistry);
    final List<Integer> partitionIds = List.of(1, 2);

    buildJob(partitionIds, metrics);

    final double gauge1 =
        meterRegistry
            .get(Metrics.GAUGE_NAME_TOTAL_PENDING_ARCHIVE_INSTANCES)
            .tag(Metrics.TAG_KEY_PARTITION, "1")
            .gauge()
            .value();
    final double gauge2 =
        meterRegistry
            .get(Metrics.GAUGE_NAME_TOTAL_PENDING_ARCHIVE_INSTANCES)
            .tag(Metrics.TAG_KEY_PARTITION, "2")
            .gauge()
            .value();

    assertThat(gauge1).isEqualTo(0.0);
    assertThat(gauge2).isEqualTo(0.0);
  }

  @Test
  void archiveBatchUpdatesGaugeValues() {
    final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    final Metrics metrics = new Metrics(meterRegistry);
    final List<Integer> partitionIds = List.of(1, 2);

    when(processInstanceTemplate.getFullQualifiedName()).thenReturn("list-view");
    when(archiver.moveDocuments(anyString(), anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final ProcessInstancesArchiverJob job = buildJob(partitionIds, metrics);

    final ArchiveBatch batch =
        new ArchiveBatch("2024-01-01", List.of("key1", "key2"), Map.of(1, 42L, 2, 17L));
    job.archiveBatch(batch).join();

    final double gauge1 =
        meterRegistry
            .get(Metrics.GAUGE_NAME_TOTAL_PENDING_ARCHIVE_INSTANCES)
            .tag(Metrics.TAG_KEY_PARTITION, "1")
            .gauge()
            .value();
    final double gauge2 =
        meterRegistry
            .get(Metrics.GAUGE_NAME_TOTAL_PENDING_ARCHIVE_INSTANCES)
            .tag(Metrics.TAG_KEY_PARTITION, "2")
            .gauge()
            .value();

    assertThat(gauge1).isEqualTo(42.0);
    assertThat(gauge2).isEqualTo(17.0);
  }

  @Test
  void archiveBatchWithNullReturnsZero() {
    final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    final Metrics metrics = new Metrics(meterRegistry);

    final ProcessInstancesArchiverJob job = buildJob(List.of(1), metrics);

    final Integer result = job.archiveBatch(null).join();

    assertThat(result).isEqualTo(0);
  }

  @Test
  void archiveBatchReturnsArchivedCount() {
    final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    final Metrics metrics = new Metrics(meterRegistry);

    when(processInstanceTemplate.getFullQualifiedName()).thenReturn("list-view");
    when(archiver.moveDocuments(anyString(), anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final ProcessInstancesArchiverJob job = buildJob(List.of(1), metrics);

    final ArchiveBatch batch =
        new ArchiveBatch("2024-01-01", List.of("key1", "key2"), Map.of(1, 2L));
    final Integer result = job.archiveBatch(batch).join();

    assertThat(result).isEqualTo(2);
  }

  @Test
  void archiveBatchWithNullResetsPreviouslyNonZeroPendingToZero() {
    final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    final Metrics metrics = new Metrics(meterRegistry);
    final List<Integer> partitionIds = List.of(1, 2);

    when(processInstanceTemplate.getFullQualifiedName()).thenReturn("list-view");
    when(archiver.moveDocuments(anyString(), anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final ProcessInstancesArchiverJob job = buildJob(partitionIds, metrics);

    // first set pending counts > 0 via a real batch
    job.archiveBatch(
            new ArchiveBatch("2024-01-01", List.of("key1", "key2"), Map.of(1, 42L, 2, 17L)))
        .join();
    assertThat(
            meterRegistry
                .get(Metrics.GAUGE_NAME_TOTAL_PENDING_ARCHIVE_INSTANCES)
                .tag(Metrics.TAG_KEY_PARTITION, "1")
                .gauge()
                .value())
        .isEqualTo(42.0);

    // null batch (nothing to archive) must reset all partitions to 0
    job.archiveBatch(null).join();

    assertThat(
            meterRegistry
                .get(Metrics.GAUGE_NAME_TOTAL_PENDING_ARCHIVE_INSTANCES)
                .tag(Metrics.TAG_KEY_PARTITION, "1")
                .gauge()
                .value())
        .isEqualTo(0.0);
    assertThat(
            meterRegistry
                .get(Metrics.GAUGE_NAME_TOTAL_PENDING_ARCHIVE_INSTANCES)
                .tag(Metrics.TAG_KEY_PARTITION, "2")
                .gauge()
                .value())
        .isEqualTo(0.0);
  }

  @Test
  void archiveBatchResetsPartitionMissingFromAggregationToZero() {
    final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    final Metrics metrics = new Metrics(meterRegistry);
    final List<Integer> partitionIds = List.of(1, 2);

    when(processInstanceTemplate.getFullQualifiedName()).thenReturn("list-view");
    when(archiver.moveDocuments(anyString(), anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final ProcessInstancesArchiverJob job = buildJob(partitionIds, metrics);

    // batch 1: both partitions have pending
    job.archiveBatch(new ArchiveBatch("2024-01-01", List.of("key1"), Map.of(1, 42L, 2, 17L)))
        .join();
    assertThat(
            meterRegistry
                .get(Metrics.GAUGE_NAME_TOTAL_PENDING_ARCHIVE_INSTANCES)
                .tag(Metrics.TAG_KEY_PARTITION, "2")
                .gauge()
                .value())
        .isEqualTo(17.0);

    // batch 2: partition 2 dropped to zero -> omitted from terms aggregation result
    job.archiveBatch(new ArchiveBatch("2024-01-02", List.of("key2"), Map.of(1, 5L))).join();

    assertThat(
            meterRegistry
                .get(Metrics.GAUGE_NAME_TOTAL_PENDING_ARCHIVE_INSTANCES)
                .tag(Metrics.TAG_KEY_PARTITION, "1")
                .gauge()
                .value())
        .isEqualTo(5.0);
    assertThat(
            meterRegistry
                .get(Metrics.GAUGE_NAME_TOTAL_PENDING_ARCHIVE_INSTANCES)
                .tag(Metrics.TAG_KEY_PARTITION, "2")
                .gauge()
                .value())
        .isEqualTo(0.0);
  }
}
