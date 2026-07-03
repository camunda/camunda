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

/**
 * Record flyweight for the {@link RatioAggregateFunction} accumulator (matched and total), two
 * longs delegated to the ZeebeDb {@link DbLong} primitive.
 */
public final class RatioAccumulatorRecordValue implements RecordValue<RatioAccumulator> {

  private final DbLong matched = new DbLong();
  private final DbLong total = new DbLong();

  @Override
  public RatioAccumulatorRecordValue wrapValue(final RatioAccumulator acc) {
    matched.wrapLong(acc.matched());
    total.wrapLong(acc.total());
    return this;
  }

  @Override
  public RatioAccumulator value() {
    return new RatioAccumulator(matched.getValue(), total.getValue());
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    matched.wrap(buffer, offset, Long.BYTES);
    total.wrap(buffer, offset + Long.BYTES, Long.BYTES);
  }

  @Override
  public int getLength() {
    return Long.BYTES * 2;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    matched.write(buffer, offset);
    total.write(buffer, offset + Long.BYTES);
    return getLength();
  }
}
