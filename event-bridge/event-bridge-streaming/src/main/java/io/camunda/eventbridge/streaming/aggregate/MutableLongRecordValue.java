/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import io.camunda.zeebe.db.impl.DbLong;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableLong;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Record flyweight for a {@link MutableLong} accumulator — the {@link SumAggregateFunction}'s
 * allocation-free running sum. Delegates to the ZeebeDb {@link DbLong} primitive, so the wire
 * layout is a single long, byte-identical to {@link LongRecordValue}: state persisted with either
 * codec restores with the other.
 *
 * <p>{@link #value()} (and thus {@link #fromBytes}) returns a fresh, owned holder — callers store
 * it as a live accumulator and mutate it. {@link #fromBytesForMerge} instead reuses one internal
 * holder across calls, honoring the merge-view contract (the delta side of a merge is read and
 * discarded, never stored or mutated), so the reduce-side per-cell delta decode allocates nothing.
 */
public final class MutableLongRecordValue implements RecordValue<MutableLong> {

  private final DbLong delegate = new DbLong();
  private final UnsafeBuffer readView = new UnsafeBuffer(0, 0);
  private final MutableLong mergeView = new MutableLong();

  @Override
  public MutableLongRecordValue wrapValue(final MutableLong value) {
    delegate.wrapLong(value.value);
    return this;
  }

  @Override
  public MutableLong value() {
    return new MutableLong(delegate.getValue());
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    delegate.wrap(buffer, offset, length);
  }

  @Override
  public int getLength() {
    return delegate.getLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return delegate.write(buffer, offset);
  }

  @Override
  public MutableLong fromBytesForMerge(final byte[] bytes) {
    readView.wrap(bytes);
    delegate.wrap(readView, 0, bytes.length);
    mergeView.set(delegate.getValue());
    return mergeView;
  }
}
