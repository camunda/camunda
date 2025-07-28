/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.multiinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.BinaryValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.MultiInstanceRecordValue;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class MultiInstanceRecord extends UnifiedRecordValue implements MultiInstanceRecordValue {
  private final ArrayProperty<BinaryValue> inputCollectionProperty =
      new ArrayProperty<>("inputCollection", BinaryValue::new);

  public MultiInstanceRecord() {
    super(1);
    declareProperty(inputCollectionProperty);
  }

  @JsonIgnore
  public List<DirectBuffer> getInputCollectionBuffers() {
    return inputCollectionProperty.stream().map(BinaryValue::getValue).collect(Collectors.toList());
  }

  @Override
  public List<String> getInputCollection() {
    return getInputCollectionBuffers().stream().map(MsgPackConverter::convertToJson).toList();
  }

  public MultiInstanceRecord setInputCollection(final List<DirectBuffer> inputCollection) {
    inputCollection.forEach(
        inputElement -> {
          inputCollectionProperty.add().wrap(inputElement, 0, inputElement.capacity());
        });
    return this;
  }
}
