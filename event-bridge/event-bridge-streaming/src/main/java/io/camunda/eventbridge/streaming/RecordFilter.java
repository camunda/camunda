/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

/**
 * Decides, from a raw payload, whether the runtime should process a record. The source loop applies
 * it <em>before</em> the (far costlier) {@link MessageDeserializer}, so a consumer that folds only
 * a subset of record types can skip decoding and enqueuing the rest by peeking the payload's
 * metadata.
 *
 * <p>A rejected record is neither decoded nor folded, but its offset is still accounted for: the
 * next accepted record's commit covers it, and a poll that <em>ends</em> on a rejected run hands
 * the processor one coalesced offset-only advance, so commits, watermark-driven seals and — when a
 * payload-timestamp peek is configured — stream time keep moving through filtered-only stretches.
 * Filtering here (rather than at the producer) keeps every record on the topic for other consumer
 * groups; each consumer skips only what it does not need.
 */
@FunctionalInterface
public interface RecordFilter {

  /** The default when no filter is configured: process every record. */
  RecordFilter ACCEPT_ALL = payload -> true;

  /** Whether the record carried by {@code payload} should be deserialized and processed. */
  boolean accept(byte[] payload);
}
