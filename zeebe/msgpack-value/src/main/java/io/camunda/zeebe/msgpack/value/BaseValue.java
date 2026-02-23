/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.value;

import io.camunda.zeebe.msgpack.Recyclable;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;

public abstract class BaseValue implements Recyclable {
  public abstract void writeJSON(StringBuilder builder);

  public abstract int write(MsgPackWriter writer);

  public abstract void read(MsgPackReader reader);

  public abstract int getEncodedLength();

  @Override
  public String toString() {
    final StringBuilder stringBuilder = new StringBuilder();
    writeJSON(stringBuilder);
    return stringBuilder.toString();
  }
}
