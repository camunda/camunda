/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/** Stores enums with up to 255 variants in a single byte. */
public final class DbEnumValue<T extends Enum<T>> implements DbKey, DbValue {
  private final DbByte value = new DbByte();
  private final T[] variants;

  public DbEnumValue(final Class<T> enumType) {
    variants = enumType.getEnumConstants();
    if (variants.length == 0) {
      throw new IllegalArgumentException("Enum type cannot be empty: " + enumType);
    }

    if (Byte.MIN_VALUE + variants.length > Byte.MAX_VALUE) {
      throw new IllegalArgumentException(
          "Enum type cannot have more than %s values: %s".formatted(Byte.MAX_VALUE, enumType));
    }
  }

  public T getValue() {
    final var ordinal = toOrdinal(value.getValue());
    if (ordinal >= variants.length) {
      throw new IllegalArgumentException("Invalid ordinal value: " + ordinal);
    }
    return variants[ordinal];
  }

  public void setValue(final T value) {
    this.value.wrapByte(fromOrdinal(value.ordinal()));
  }

  private static byte fromOrdinal(final int ordinal) {
    return (byte) (Byte.MIN_VALUE + ordinal);
  }

  private static int toOrdinal(final byte value) {
    return value - Byte.MIN_VALUE;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    value.wrap(buffer, offset, length);
  }

  @Override
  public int getLength() {
    return value.getLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    value.write(buffer, offset);
  }
}
