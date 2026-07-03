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
 * Record flyweight for a {@code Long} accumulator, e.g. for count/sum aggregates. Delegates to the
 * ZeebeDb {@link DbLong} primitive so the wire layout matches the store's own long encoding.
 */
public final class LongRecordValue implements RecordValue<Long> {

  private final DbLong delegate = new DbLong();

  @Override
  public LongRecordValue wrapValue(final Long value) {
    delegate.wrapLong(value);
    return this;
  }

  @Override
  public Long value() {
    return delegate.getValue();
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
}
