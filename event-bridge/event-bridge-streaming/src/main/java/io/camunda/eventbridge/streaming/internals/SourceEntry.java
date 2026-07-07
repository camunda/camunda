/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

/**
 * One item handed from the source stage to a partition's processor: either a successfully decoded
 * record or a decode failure carried forward so the exception policy is applied in per-partition
 * offset order by the processor, not on the shared source thread. Every item carries its source
 * offset so the processor can dedup its resume gap and advance the pending commit position.
 *
 * @param <R> the decoded record type
 */
public sealed interface SourceEntry<R>
    permits SourceEntry.Decoded, SourceEntry.DecodeFailure, SourceEntry.Filtered {

  /** The source offset this item was decoded from. */
  long offset();

  /** A record that decoded successfully. */
  record Decoded<R>(long offset, R record) implements SourceEntry<R> {}

  /**
   * A record whose decode threw. The cause is replayed to the exception policy by the processor, so
   * a poison record fails or skips deterministically at its exact offset rather than off-thread.
   */
  record DecodeFailure<R>(long offset, RuntimeException cause) implements SourceEntry<R> {}

  /**
   * A coalesced run of filter-rejected records ending at {@code offset} — nothing to fold, but the
   * processor advances its pending commit position past the run, so the commit clock and the
   * watermark-driven segment seals keep moving during filtered-only stretches. {@code eventTimeMs}
   * is the run's highest event time when the source can peek it off the raw payload (so event-time
   * windows keep closing too), or {@link Long#MIN_VALUE} when it cannot — an unknown time leaves
   * stream time untouched.
   */
  record Filtered<R>(long offset, long eventTimeMs) implements SourceEntry<R> {}
}
