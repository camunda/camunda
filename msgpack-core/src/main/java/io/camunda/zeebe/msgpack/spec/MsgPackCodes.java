/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.spec;

import java.nio.ByteOrder;

public final class MsgPackCodes {
  public static final byte POSFIXINT_MASK = (byte) 0x80;
  public static final byte FIXMAP_PREFIX = (byte) 0x80;
  public static final byte FIXARRAY_PREFIX = (byte) 0x90;
  public static final byte FIXSTR_PREFIX = (byte) 0xa0;
  public static final byte NIL = (byte) 0xc0;
  public static final byte NEVER_USED = (byte) 0xc1;
  public static final byte FALSE = (byte) 0xc2;
  public static final byte TRUE = (byte) 0xc3;
  public static final byte BIN8 = (byte) 0xc4;
  public static final byte BIN16 = (byte) 0xc5;
  public static final byte BIN32 = (byte) 0xc6;
  public static final byte EXT8 = (byte) 0xc7;
  public static final byte EXT16 = (byte) 0xc8;
  public static final byte EXT32 = (byte) 0xc9;
  public static final byte FLOAT32 = (byte) 0xca;
  public static final byte FLOAT64 = (byte) 0xcb;
  public static final byte UINT8 = (byte) 0xcc;
  public static final byte UINT16 = (byte) 0xcd;
  public static final byte UINT32 = (byte) 0xce;
  public static final byte UINT64 = (byte) 0xcf;
  public static final byte INT8 = (byte) 0xd0;
  public static final byte INT16 = (byte) 0xd1;
  public static final byte INT32 = (byte) 0xd2;
  public static final byte INT64 = (byte) 0xd3;
  public static final byte FIXEXT1 = (byte) 0xd4;
  public static final byte FIXEXT2 = (byte) 0xd5;
  public static final byte FIXEXT4 = (byte) 0xd6;
  public static final byte FIXEXT8 = (byte) 0xd7;
  public static final byte FIXEXT16 = (byte) 0xd8;
  public static final byte STR8 = (byte) 0xd9;
  public static final byte STR16 = (byte) 0xda;
  public static final byte STR32 = (byte) 0xdb;
  public static final byte ARRAY16 = (byte) 0xdc;
  public static final byte ARRAY32 = (byte) 0xdd;
  public static final byte MAP16 = (byte) 0xde;
  public static final byte MAP32 = (byte) 0xdf;
  public static final byte NEGFIXINT_PREFIX = (byte) 0xe0;
  public static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

  public static boolean isFixInt(final byte b) {
    final int v = b & 0xFF;
    return v <= 0x7f || v >= 0xe0;
  }

  public static boolean isPosFixInt(final byte b) {
    return (b & POSFIXINT_MASK) == 0;
  }

  public static boolean isNegFixInt(final byte b) {
    return (b & NEGFIXINT_PREFIX) == NEGFIXINT_PREFIX;
  }

  public static boolean isFixStr(final byte b) {
    return (b & (byte) 0xe0) == FIXSTR_PREFIX;
  }

  public static boolean isFixedArray(final byte b) {
    return (b & (byte) 0xf0) == FIXARRAY_PREFIX;
  }

  public static boolean isFixedMap(final byte b) {
    return (b & (byte) 0xf0) == FIXMAP_PREFIX;
  }

  public static boolean isFixedRaw(final byte b) {
    return (b & (byte) 0xe0) == FIXSTR_PREFIX;
  }

  public static boolean isMap(final byte b) {
    return isFixedMap(b) || b == MAP16 || b == MAP32;
  }
}
