/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.msgpack;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public final class Pojo extends UnpackedObject {

  private final EnumProperty<POJOEnum> enumProp = new EnumProperty<>("enumProp", POJOEnum.class);
  private final LongProperty longProp = new LongProperty("longProp");
  private final IntegerProperty intProp = new IntegerProperty("intProp");
  private final StringProperty stringProp = new StringProperty("stringProp");
  private final BinaryProperty binaryProp = new BinaryProperty("binaryProp");
  private final ObjectProperty<Nested> objectProp =
      new ObjectProperty<>("objectProp", new Nested());

  public Pojo() {
    super(6);
    declareProperty(enumProp)
        .declareProperty(longProp)
        .declareProperty(intProp)
        .declareProperty(stringProp)
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

  public DirectBuffer getBinary() {
    return binaryProp.getValue();
  }

  public void setBinary(final DirectBuffer buffer) {
    binaryProp.setValue(buffer, 0, buffer.capacity());
  }

  public Nested nestedObject() {
    return objectProp.getValue();
  }

  public static final class Nested extends UnpackedObject {
    private final LongProperty longProp = new LongProperty("foo", -1L);

    public Nested() {
      super(1);
      declareProperty(longProp);
    }

    public long getLong() {
      return longProp.getValue();
    }

    public Nested setLong(final long value) {
      longProp.setValue(value);
      return this;
    }
  }

  public enum POJOEnum {
    FOO,
    BAR,
    BAZ,
    QUX,
    REALLY_LONG_ENUM;
  }
}
