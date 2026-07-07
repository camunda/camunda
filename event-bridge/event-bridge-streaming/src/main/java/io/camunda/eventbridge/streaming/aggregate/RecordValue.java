/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import io.camunda.zeebe.db.DbValue;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A reusable ZeebeDb record flyweight that maps to and from a domain value {@code T} — the same
 * self-serializing record-value machinery the engines use for persisted state (e.g. a persisted
 * broker exposing {@code wrap(BrokerMetadata)} / {@code toMetadata()}). Implementations are {@code
 * UnpackedObject} flyweights whose msgpack properties define the wire layout once, replacing the
 * hand-rolled byte codecs the aggregations used to carry arbitrary grouping keys and accumulators.
 *
 * <p>The framework stays agnostic to the concrete type: the durable aggregations hold their shared,
 * heterogeneous cells in byte-keyed stores and the shuffle carries opaque payloads, so both need a
 * {@code byte[]} view of the value. The {@link #toBytes}/{@link #fromBytes} defaults derive that
 * from the flyweight's own {@link DbValue} serialization, so a domain only implements the two
 * {@code wrapValue}/{@link #value()} halves.
 *
 * <p>A single flyweight instance is mutable and reused; each call is self-contained ({@code
 * wrapValue} then serialize, or {@code wrap} then {@code value}), so one owner must not interleave
 * uses. Give each consumer its own instance rather than sharing one.
 *
 * @param <T> the domain value type
 */
public interface RecordValue<T> extends DbValue {

  /** Loads {@code value} into this flyweight so it can be serialized; returns {@code this}. */
  RecordValue<T> wrapValue(T value);

  /** The domain value currently held by this flyweight. */
  T value();

  /** Serializes {@code value} to a fresh byte array via this flyweight's record layout. */
  default byte[] toBytes(final T value) {
    wrapValue(value);
    final byte[] out = new byte[getLength()];
    write(new UnsafeBuffer(out), 0);
    return out;
  }

  /** Reads a domain value from bytes previously produced by {@link #toBytes}. */
  default T fromBytes(final byte[] bytes) {
    wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    return value();
  }

  /**
   * Reads a domain value from bytes for <em>merge-only</em> consumption: the result may be a
   * read-only view aliasing {@code bytes}, so it must only ever be passed as the delta argument of
   * a merge and discarded — never stored as an accumulator, mutated, or re-serialized. The default
   * is the full heap decode of {@link #fromBytes}; implementations override it when a zero-copy
   * read-only view exists (e.g. wrapping a serialized sketch instead of heapifying it).
   */
  default T fromBytesForMerge(final byte[] bytes) {
    return fromBytes(bytes);
  }
}
