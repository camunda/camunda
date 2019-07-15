/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.property;

import static io.zeebe.msgpack.value.DocumentValue.EMPTY_DOCUMENT;

import io.zeebe.msgpack.MsgpackPropertyException;
import io.zeebe.msgpack.value.DocumentValue;
import org.agrona.DirectBuffer;

public class DocumentProperty extends BaseProperty<DocumentValue> {
  public DocumentProperty(String keyString) {
    super(
        keyString,
        new DocumentValue(),
        new DocumentValue(EMPTY_DOCUMENT, 0, EMPTY_DOCUMENT.capacity()));
  }

  public DirectBuffer getValue() {
    return resolveValue().getValue();
  }

  public void setValue(DirectBuffer data) {
    setValue(data, 0, data.capacity());
  }

  public void setValue(DirectBuffer data, int offset, int length) {
    try {
      this.value.wrap(data, offset, length);
      this.isSet = true;
    } catch (Exception e) {
      throw new MsgpackPropertyException(key, e);
    }
  }
}
