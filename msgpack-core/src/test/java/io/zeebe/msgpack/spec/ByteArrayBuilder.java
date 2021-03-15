/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.spec;

public final class ByteArrayBuilder {
  protected byte[] value = new byte[0];

  /**
   * NOTE: arguments are not converted to bytes (i.e. int becomes 4 byte) but the arguments are cast
   * to byte (i.e. lowest 8 bits are kept). This method exists solely for convenience to avoid
   * explicit casts to byte where this builder is used.
   */
  protected ByteArrayBuilder add(final int... toAdd) {
    final byte[] arr = new byte[toAdd.length];
    for (int i = 0; i < toAdd.length; i++) {
      arr[i] = (byte) toAdd[i];
    }
    return add(arr);
  }

  protected ByteArrayBuilder add(final byte... toAdd) {
    final byte[] newValue = new byte[value.length + toAdd.length];
    System.arraycopy(value, 0, newValue, 0, value.length);
    System.arraycopy(toAdd, 0, newValue, value.length, toAdd.length);
    value = newValue;
    return this;
  }
}
