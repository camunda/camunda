/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

import io.camunda.eventbridge.streaming.Task;

/**
 * The mutable per-partition state the runtime shards on: the partition's decoded-record queue, its
 * {@link Task}, its dedup baseline, and the working values (pending commit offset, stream time,
 * commit/punctuation clocks) advanced as records are processed.
 *
 * <p><b>Concurrency.</b> Everything here except the thread-safe {@link #queue} is single-writer
 * under the partition's lease: only the current leaseholder mutates {@link #pending}, {@link
 * #streamTime}, and the clock fields, and it does so between leasing and releasing. The {@link
 * PartitionScheduler} reads these fields only for an <em>unleased</em> partition and only under its
 * own lock, which the leaseholder also takes to release — so the release publishes the
 * leaseholder's writes to the next reader. The queue and {@link #baseline} are safe to read from
 * the source thread (baseline is fixed once the partition is materialized).
 *
 * @param <R> the decoded record type
 */
public final class Partition<R> {

  private final int id;
  private final PartitionQueue<R> queue;
  private final Task<R> task;
  private final long baseline;

  private long pending = Task.NO_OFFSET;
  private long streamTime = Long.MIN_VALUE;
  private long lastCommitNanos;
  private long lastPunctuationNanos;

  public Partition(
      final int id,
      final PartitionQueue<R> queue,
      final Task<R> task,
      final long baseline,
      final long nowNanos) {
    this.id = id;
    this.queue = queue;
    this.task = task;
    this.baseline = baseline;
    lastCommitNanos = nowNanos;
    lastPunctuationNanos = nowNanos;
  }

  public int id() {
    return id;
  }

  public PartitionQueue<R> queue() {
    return queue;
  }

  public Task<R> task() {
    return task;
  }

  /** Offset at or below which records are already folded into durable state (skipped on resume). */
  public long baseline() {
    return baseline;
  }

  /**
   * Highest processed offset not yet committed, or {@link Task#NO_OFFSET} if nothing is pending.
   */
  public long pending() {
    return pending;
  }

  public boolean hasPending() {
    return pending != Task.NO_OFFSET;
  }

  /** Advances the pending commit offset to the highest processed so far. */
  public void markProcessed(final long offset) {
    if (offset > pending) {
      pending = offset;
    }
  }

  /** Clears the pending offset after a commit made it durable. */
  public void clearPending() {
    pending = Task.NO_OFFSET;
  }

  /** Max event timestamp seen, or {@link Long#MIN_VALUE} if none / no timestamp extractor. */
  public long streamTime() {
    return streamTime;
  }

  public boolean hasStreamTime() {
    return streamTime != Long.MIN_VALUE;
  }

  public void observeStreamTime(final long timestampMs) {
    if (timestampMs > streamTime) {
      streamTime = timestampMs;
    }
  }

  public long lastCommitNanos() {
    return lastCommitNanos;
  }

  public void markCommitted(final long nowNanos) {
    lastCommitNanos = nowNanos;
  }

  public long lastPunctuationNanos() {
    return lastPunctuationNanos;
  }

  public void markPunctuated(final long nowNanos) {
    lastPunctuationNanos = nowNanos;
  }
}
