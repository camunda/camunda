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
public sealed interface SourceEntry<R> permits SourceEntry.Decoded, SourceEntry.DecodeFailure {

  /** The source offset this item was decoded from. */
  long offset();

  /** A record that decoded successfully. */
  record Decoded<R>(long offset, R record) implements SourceEntry<R> {}

  /**
   * A record whose decode threw. The cause is replayed to the exception policy by the processor, so
   * a poison record fails or skips deterministically at its exact offset rather than off-thread.
   */
  record DecodeFailure<R>(long offset, RuntimeException cause) implements SourceEntry<R> {}
}
