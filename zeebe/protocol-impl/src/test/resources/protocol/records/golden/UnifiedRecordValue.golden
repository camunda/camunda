/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.UnpackedObject;

/** A {@link MsgpackRecordValue} whose msgpack format is declared with properties. */
public class UnifiedRecordValue extends UnpackedObject implements MsgpackRecordValue {

  /**
   * Creates a new {@link UnifiedRecordValue}.
   *
   * @param expectedDeclaredProperties the expected number of declared properties. Providing the
   *     correct number helps to avoid allocations and memory copies.
   */
  public UnifiedRecordValue(final int expectedDeclaredProperties) {
    super(expectedDeclaredProperties);
  }

  @Override
  @JsonIgnore
  public int getLength() {
    return super.getLength();
  }

  @Override
  @JsonIgnore
  public int getEncodedLength() {
    return super.getEncodedLength();
  }

  @Override
  @JsonIgnore
  public boolean isEmpty() {
    return super.isEmpty();
  }
}
