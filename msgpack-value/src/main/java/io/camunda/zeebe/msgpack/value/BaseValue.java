/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.value;

import io.zeebe.msgpack.Recyclable;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;

public abstract class BaseValue implements Recyclable {
  public abstract void writeJSON(StringBuilder builder);

  public abstract void write(MsgPackWriter writer);

  public abstract void read(MsgPackReader reader);

  public abstract int getEncodedLength();

  @Override
  public String toString() {
    final StringBuilder stringBuilder = new StringBuilder();
    writeJSON(stringBuilder);
    return stringBuilder.toString();
  }
}
