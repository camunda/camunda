/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.property;

import static io.zeebe.msgpack.value.DocumentValue.EMPTY_DOCUMENT;

import io.zeebe.msgpack.MsgpackPropertyException;
import io.zeebe.msgpack.value.DocumentValue;
import org.agrona.DirectBuffer;

public final class DocumentProperty extends BaseProperty<DocumentValue> {
  public DocumentProperty(final String keyString) {
    super(
        keyString,
        new DocumentValue(),
        new DocumentValue(EMPTY_DOCUMENT, 0, EMPTY_DOCUMENT.capacity()));
  }

  public DirectBuffer getValue() {
    return resolveValue().getValue();
  }

  public void setValue(final DirectBuffer data) {
    setValue(data, 0, data.capacity());
  }

  public void setValue(final DirectBuffer data, final int offset, final int length) {
    try {
      value.wrap(data, offset, length);
      isSet = true;
    } catch (final Exception e) {
      throw new MsgpackPropertyException(key, e);
    }
  }
}
