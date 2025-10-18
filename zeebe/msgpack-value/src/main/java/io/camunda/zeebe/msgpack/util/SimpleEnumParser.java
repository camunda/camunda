/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.util;

import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;

/**
 * Simple enum parser that allocates a new String for each parse operation.
 *
 * @param <E> the enum type
 */
public class SimpleEnumParser<E extends Enum<E>> implements EnumParser<E> {
  private final Class<E> enumClass;

  public SimpleEnumParser(final Class<E> enumClass) {
    this.enumClass = enumClass;
  }

  @Override
  public E parse(final DirectBuffer buffer, final int offset, final int length) {
    try {
      final byte[] bytes = new byte[length];
      buffer.getBytes(offset, bytes);
      final String enumName = new String(bytes, StandardCharsets.US_ASCII);
      return Enum.valueOf(enumClass, enumName);
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }
}
