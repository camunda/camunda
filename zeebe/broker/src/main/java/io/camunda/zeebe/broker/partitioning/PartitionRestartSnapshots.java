/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes a snapshot of every partition a member is about to restart, so that the restarted replicas
 * replay less of the log. Never holds up the restart beyond its bound and never fails it: a
 * partition without a new snapshot restarts from the snapshot it has.
 */
@NullMarked
public final class PartitionRestartSnapshots {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionRestartSnapshots.class);

  private final Duration bound;

  /**
   * @param bound how long all snapshots together may take
   */
  public PartitionRestartSnapshots(final Duration bound) {
    this.bound = bound;
  }

  /**
   * Takes a snapshot of every given partition.
   *
   * @return completes once every snapshot was taken, skipped or given up, never exceptionally
   */
  public CompletableFuture<List<PartitionSnapshot>> take(
      final Collection<? extends SnapshotPartition> partitions) {
    final var started = System.nanoTime();
    final var snapshots = partitions.stream().map(partition -> take(partition, started)).toList();
    return CompletableFuture.allOf(snapshots.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> snapshots.stream().map(CompletableFuture::join).toList());
  }

  private CompletableFuture<PartitionSnapshot> take(
      final SnapshotPartition partition, final long started) {
    final var result = new CompletableFuture<PartitionSnapshot>();
    CompletableFuture.delayedExecutor(bound.toNanos(), TimeUnit.NANOSECONDS)
        .execute(
            () ->
                result.complete(
                    new PartitionSnapshot(
                        partition.partition(), Outcome.TIMED_OUT, null, since(started), null)));
    try {
      partition
          .forceSnapshot()
          .whenComplete(
              (snapshot, error) -> {
                if (error != null) {
                  result.complete(
                      new PartitionSnapshot(
                          partition.partition(),
                          Outcome.FAILED,
                          null,
                          since(started),
                          error.toString()));
                } else {
                  result.complete(
                      new PartitionSnapshot(
                          partition.partition(),
                          snapshot == null ? Outcome.SKIPPED : Outcome.TAKEN,
                          snapshot,
                          since(started),
                          null));
                }
              });
    } catch (final RuntimeException e) {
      result.complete(
          new PartitionSnapshot(
              partition.partition(), Outcome.FAILED, null, since(started), e.toString()));
    }
    return result.whenComplete((snapshot, error) -> log(snapshot));
  }

  private static void log(final PartitionSnapshot snapshot) {
    if (snapshot.outcome() == Outcome.TAKEN && snapshot.snapshot() != null) {
      LOG.info(
          "Took a snapshot of partition {} before restarting it in {} ms: processed position {},"
              + " {} bytes",
          snapshot.partition(),
          snapshot.took().toMillis(),
          snapshot.snapshot().processedPosition(),
          snapshot.snapshot().sizeInBytes());
    } else {
      LOG.info(
          "Restarting partition {} from its previous snapshot, no new one was taken ({}{})",
          snapshot.partition(),
          snapshot.outcome(),
          snapshot.failure() == null ? "" : ": " + snapshot.failure());
    }
  }

  private static Duration since(final long startedNanos) {
    return Duration.ofNanos(System.nanoTime() - startedNanos);
  }

  /** The replica of a partition on this member, before its restart. */
  public interface SnapshotPartition {
    int partition();

    /** Completes with null if no snapshot was taken, for one already being taken. */
    CompletableFuture<@Nullable TakenSnapshot> forceSnapshot();
  }

  /**
   * @param processedPosition the last position the snapshot covers
   * @param sizeInBytes the snapshot's size on disk
   */
  public record TakenSnapshot(long processedPosition, long sizeInBytes) {}

  /** How the snapshot of one partition went. */
  public enum Outcome {
    /** A new snapshot was taken. */
    TAKEN,
    /** No snapshot was taken, as one was already being taken or nothing changed since the last. */
    SKIPPED,
    /** Taking the snapshot failed. */
    FAILED,
    /** The bound passed before the snapshot was taken. */
    TIMED_OUT
  }

  public record PartitionSnapshot(
      int partition,
      Outcome outcome,
      @Nullable TakenSnapshot snapshot,
      Duration took,
      @Nullable String failure) {}
}
