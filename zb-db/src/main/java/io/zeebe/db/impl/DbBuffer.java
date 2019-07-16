/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl;

import io.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class DbBuffer implements DbValue {

  private final ExpandableArrayBuffer value = new ExpandableArrayBuffer();
  private final DirectBuffer view = new UnsafeBuffer(0, 0);

  public void wrapBuffer(DirectBuffer buffer, int offset, int length) {
    view.wrap(buffer, offset, length);
  }

  public void wrapBuffer(DirectBuffer buffer) {
    view.wrap(buffer);
  }

  public DirectBuffer getValue() {
    return view;
  }

  @Override
  public int getLength() {
    return view.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    buffer.putBytes(offset, view, 0, view.capacity());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    value.putBytes(0, buffer, offset, length);
    view.wrap(value, 0, length);
  }
}
