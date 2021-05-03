/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack;

import io.zeebe.msgpack.property.BinaryProperty;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.ObjectProperty;
import io.zeebe.msgpack.property.PackedProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public final class POJO extends UnpackedObject {

  private final EnumProperty<POJOEnum> enumProp = new EnumProperty<>("enumProp", POJOEnum.class);
  private final LongProperty longProp = new LongProperty("longProp");
  private final IntegerProperty intProp = new IntegerProperty("intProp");
  private final StringProperty stringProp = new StringProperty("stringProp");
  private final PackedProperty packedProp = new PackedProperty("packedProp");
  private final BinaryProperty binaryProp = new BinaryProperty("binaryProp");
  private final ObjectProperty<POJONested> objectProp =
      new ObjectProperty<>("objectProp", new POJONested());

  public POJO() {
    declareProperty(enumProp)
        .declareProperty(longProp)
        .declareProperty(intProp)
        .declareProperty(stringProp)
        .declareProperty(packedProp)
        .declareProperty(binaryProp)
        .declareProperty(objectProp);
  }

  public POJOEnum getEnum() {
    return enumProp.getValue();
  }

  public void setEnum(final POJOEnum val) {
    enumProp.setValue(val);
  }

  public long getLong() {
    return longProp.getValue();
  }

  public void setLong(final long val) {
    longProp.setValue(val);
  }

  public int getInt() {
    return intProp.getValue();
  }

  public void setInt(final int val) {
    intProp.setValue(val);
  }

  public DirectBuffer getString() {
    return stringProp.getValue();
  }

  public void setString(final DirectBuffer buffer) {
    stringProp.setValue(buffer);
  }

  public DirectBuffer getPacked() {
    return packedProp.getValue();
  }

  public void setPacked(final DirectBuffer buffer) {
    packedProp.setValue(buffer, 0, buffer.capacity());
  }

  public DirectBuffer getBinary() {
    return binaryProp.getValue();
  }

  public void setBinary(final DirectBuffer buffer) {
    binaryProp.setValue(buffer, 0, buffer.capacity());
  }

  public POJONested nestedObject() {
    return objectProp.getValue();
  }

  public enum POJOEnum {
    FOO,
    BAR;
  }
}
