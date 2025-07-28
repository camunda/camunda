/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.multiinstance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

public class PersistedInputCollection extends UnpackedObject implements DbValue {
  private final ArrayProperty<StringValue> inputCollectionProperty =
      new ArrayProperty<>("inputCollection", StringValue::new);

  public PersistedInputCollection() {
    super(0);
    declareProperty(inputCollectionProperty);
  }

  public List<DirectBuffer> getInputCollection() {
    return inputCollectionProperty.stream()
        .map(element -> BufferUtil.cloneBuffer(element.getValue()))
        .toList();
  }

  public PersistedInputCollection setInputCollection(final List<DirectBuffer> inputCollection) {
    inputCollectionProperty.reset();
    inputCollection.forEach(
        element -> inputCollectionProperty.add().wrap(BufferUtil.cloneBuffer(element)));
    return this;
  }
}
