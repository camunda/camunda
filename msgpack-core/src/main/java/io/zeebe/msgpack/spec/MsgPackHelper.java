/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.spec;

public final class MsgPackHelper {
  public static final byte[] EMTPY_OBJECT = new byte[] {MsgPackCodes.FIXMAP_PREFIX};
  public static final byte[] EMPTY_ARRAY = new byte[] {MsgPackCodes.FIXARRAY_PREFIX};
  public static final byte[] NIL = new byte[] {MsgPackCodes.NIL};

  static long ensurePositive(final long size) {
    if (size < 0) {
      throw new MsgpackException(
          "Negative value should not be accepted by size value and unsigned 64bit integer");
    } else {
      return size;
    }
  }
}
