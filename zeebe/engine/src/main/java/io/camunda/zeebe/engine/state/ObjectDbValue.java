/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import org.agrona.MutableDirectBuffer;

public abstract class ObjectDbValue extends UnpackedObject implements DbValue {

  private int serializeLength = -1;

  /**
   * Creates a new UnpackedObject
   *
   * @param expectedDeclaredProperties a size hint for the number of declared properties. Providing
   *     the correct number helps to avoid allocations and memory copies.
   */
  public ObjectDbValue(final int expectedDeclaredProperties) {
    super(expectedDeclaredProperties);
  }

  @Override
  public int getLength() {
    if (serializeLength == -1) {
      return super.getLength();
    } else {
      return serializeLength;
    }
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    serializeLength = super.write(buffer, offset);
    return serializeLength;
  }

  @Override
  public void reset() {
    super.reset();
    serializeLength = -1;
  }
}
