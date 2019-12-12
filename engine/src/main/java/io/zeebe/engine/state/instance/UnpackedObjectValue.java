/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class UnpackedObjectValue implements DbValue {

  private UnpackedObject value;

  public void wrapObject(final UnpackedObject value) {
    this.value = value;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    value.wrap(buffer, offset, length);
  }

  @Override
  public int getLength() {
    return value.getLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    value.write(buffer, offset);
  }

  public UnpackedObject getObject() {
    return value;
  }
}
