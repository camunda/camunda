/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.msgpack;

import io.camunda.zeebe.msgpack.POJO.POJOEnum;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.PackedProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public final class AllTypesDefaultValuesPOJO extends UnpackedObject {

  private final EnumProperty<POJOEnum> enumProp;
  private final LongProperty longProp;
  private final IntegerProperty intProp;
  private final StringProperty stringProp;
  private final PackedProperty packedProp;
  private final BinaryProperty binaryProp;
  private final ObjectProperty<POJONested> objectProp;

  public AllTypesDefaultValuesPOJO(
      final POJOEnum enumDefault,
      final long longDefault,
      final int intDefault,
      final String stringDefault,
      final DirectBuffer packedDefault,
      final DirectBuffer binaryDefault,
      final POJONested objectDefault) {
    enumProp = new EnumProperty<>("enumProp", POJOEnum.class, enumDefault);
    longProp = new LongProperty("longProp", longDefault);
    intProp = new IntegerProperty("intProp", intDefault);
    stringProp = new StringProperty("stringProp", stringDefault);
    packedProp = new PackedProperty("packedProp", packedDefault);
    binaryProp = new BinaryProperty("binaryProp", binaryDefault);
    objectProp = new ObjectProperty<>("objectProp", objectDefault);

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

  public long getLong() {
    return longProp.getValue();
  }

  public int getInt() {
    return intProp.getValue();
  }

  public DirectBuffer getString() {
    return stringProp.getValue();
  }

  public DirectBuffer getPacked() {
    return packedProp.getValue();
  }

  public DirectBuffer getBinary() {
    return binaryProp.getValue();
  }

  public POJONested getNestedObject() {
    return objectProp.getValue();
  }
}
