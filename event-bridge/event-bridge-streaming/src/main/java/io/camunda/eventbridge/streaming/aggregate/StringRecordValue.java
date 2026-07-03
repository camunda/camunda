/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import io.camunda.zeebe.db.impl.DbString;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Record flyweight for a {@code String} grouping key. Delegates to the ZeebeDb {@link DbString}
 * primitive, so it is reusable for any aggregation keyed by a single string dimension, such as a
 * tenant id.
 */
public final class StringRecordValue implements RecordValue<String> {

  private final DbString delegate = new DbString();

  @Override
  public StringRecordValue wrapValue(final String value) {
    delegate.wrapString(value);
    return this;
  }

  @Override
  public String value() {
    return delegate.toString();
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
