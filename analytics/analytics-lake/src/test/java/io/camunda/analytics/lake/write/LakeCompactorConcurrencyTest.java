/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.LakeConfig;
import io.camunda.analytics.lake.sink.Descriptor;
import io.camunda.analytics.lake.sink.pipeline.DirectCommitSink;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Race smoke test for Fix 3: two flush threads committing descriptors for two different source
 * partitions of the SAME table through a shared {@link DirectCommitSink} — exactly how {@code
 * LakePocApp} shares one sink across every partition's own flush thread — while a third thread
 * repeatedly drives {@link LakeCompactor#compactIfNeeded()} against that same table. Before Fix 3,
 * {@link LakeCompactor}'s own javadoc claimed this could never happen ("same single poll-loop
 * thread"); that stopped being true once {@link DirectCommitSink} started committing directly from
 * flush threads.
 *
 * <p>Bounded iteration counts (no {@code Thread.sleep}, no polling loop) keep this deterministic
 * about when it stops, not about how the three threads interleave.
 */
class LakeCompactorConcurrencyTest {

  private static final int PARTITION_A = 0;
  private static final int PARTITION_B = 1;
  private static final int DESCRIPTORS_PER_PARTITION = 25;
  private static final int COMPACTION_ITERATIONS = 25;

  @Test
  void shouldNotRaceDirectCommitSinkAgainstConcurrentCompaction(@TempDir final Path tempDir)
      throws Exception {
    final LakeConfig config =
        new LakeConfig(
            "http://localhost:0",
            "race-topic",
            "race-group",
            tempDir.resolve("warehouse"),
            tempDir.resolve("state"),
            1,
            2000L,
            0L,
            0L,
            0,
            null);
    final IcebergLakeWriter writer = new IcebergLakeWriter(config);
    try {
      final Table instancesTable = writer.instancesTable();
      // Shared by both partitions' committer tasks -- exactly LakePocApp's own "one sink per
      // table, shared across every partition" wiring.
      final DirectCommitSink sink =
          new DirectCommitSink(instancesTable, writer.commitLock(instancesTable));
      final LakeCompactor compactor = new LakeCompactor(writer);

      final AtomicReference<Throwable> failure = new AtomicReference<>();
      final CountDownLatch startLatch = new CountDownLatch(1);
      final ExecutorService pool = Executors.newFixedThreadPool(3);
      try {
        pool.execute(committerTask(sink, PARTITION_A, startLatch, failure));
        pool.execute(committerTask(sink, PARTITION_B, startLatch, failure));
        pool.execute(
            () -> {
              try {
                startLatch.await();
                for (int i = 0; i < COMPACTION_ITERATIONS; i++) {
                  compactor.compactIfNeeded();
                }
              } catch (final Throwable t) {
                failure.compareAndSet(null, t);
              }
            });

        startLatch.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS))
            .as("all three threads finished within the timeout")
            .isTrue();
      } finally {
        pool.shutdownNow();
      }

      assertThat(failure.get()).as("no thread observed an unexpected exception").isNull();

      // and: both partitions' offsets/frontiers are exactly what was last committed -- no lost
      // update from an unsynchronized read-summary-then-append race between the two committer
      // threads or against a concurrent compaction pass.
      instancesTable.refresh();
      final Snapshot current = instancesTable.currentSnapshot();
      assertThat(current).as("instances table has a current snapshot").isNotNull();
      final String expectedLastOffset = Long.toString(DESCRIPTORS_PER_PARTITION - 1);
      assertThat(current.summary().get(IcebergLakeWriter.OFFSET_PROPERTY_PREFIX + PARTITION_A))
          .isEqualTo(expectedLastOffset);
      assertThat(current.summary().get(IcebergLakeWriter.OFFSET_PROPERTY_PREFIX + PARTITION_B))
          .isEqualTo(expectedLastOffset);
      assertThat(current.summary().get(DirectCommitSink.FRONTIER_PROPERTY_PREFIX + PARTITION_A))
          .isEqualTo(expectedLastOffset);
      assertThat(current.summary().get(DirectCommitSink.FRONTIER_PROPERTY_PREFIX + PARTITION_B))
          .isEqualTo(expectedLastOffset);
    } finally {
      writer.close();
    }
  }

  private static Runnable committerTask(
      final DirectCommitSink sink,
      final int partition,
      final CountDownLatch startLatch,
      final AtomicReference<Throwable> failure) {
    return () -> {
      try {
        startLatch.await();
        for (int offset = 0; offset < DESCRIPTORS_PER_PARTITION; offset++) {
          sink.accept(descriptorFor(partition, offset));
        }
      } catch (final Throwable t) {
        failure.compareAndSet(null, t);
      }
    };
  }

  /**
   * A descriptor with no data files -- exercises the offset/frontier commit race this test targets
   * without needing to write real Parquet files (a metadata-only append, the same shape {@link
   * IcebergLakeWriter}'s own "no rows this flush" path already relies on).
   */
  private static Descriptor descriptorFor(final int partition, final long offset) {
    return new Descriptor("instances", partition, List.of(), offset, offset, offset);
  }
}
