/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.spec;

import static io.zeebe.msgpack.spec.MsgPackCodes.isFixStr;
import static io.zeebe.msgpack.spec.MsgPackCodes.isFixedArray;
import static io.zeebe.msgpack.spec.MsgPackCodes.isFixedMap;
import static io.zeebe.msgpack.spec.MsgPackCodes.isNegFixInt;
import static io.zeebe.msgpack.spec.MsgPackCodes.isPosFixInt;
import static io.zeebe.msgpack.spec.MsgPackType.ARRAY;
import static io.zeebe.msgpack.spec.MsgPackType.BINARY;
import static io.zeebe.msgpack.spec.MsgPackType.EXTENSION;
import static io.zeebe.msgpack.spec.MsgPackType.FLOAT;
import static io.zeebe.msgpack.spec.MsgPackType.INTEGER;
import static io.zeebe.msgpack.spec.MsgPackType.MAP;
import static io.zeebe.msgpack.spec.MsgPackType.STRING;

/** Describes the list of the message format types defined in the MessagePack specification. */
public enum MsgPackFormat {
  // INT7
  POSFIXINT(INTEGER),
  // MAP4
  FIXMAP(MAP),
  // ARRAY4
  FIXARRAY(ARRAY),
  // STR5
  FIXSTR(STRING),
  NIL(MsgPackType.NIL),
  NEVER_USED(MsgPackType.NEVER_USED),
  BOOLEAN(MsgPackType.BOOLEAN),
  BIN8(BINARY),
  BIN16(BINARY),
  BIN32(BINARY),
  EXT8(EXTENSION),
  EXT16(EXTENSION),
  EXT32(EXTENSION),
  FLOAT32(FLOAT),
  FLOAT64(FLOAT),
  UINT8(INTEGER),
  UINT16(INTEGER),
  UINT32(INTEGER),
  UINT64(INTEGER),

  INT8(INTEGER),
  INT16(INTEGER),
  INT32(INTEGER),
  INT64(INTEGER),
  FIXEXT1(EXTENSION),
  FIXEXT2(EXTENSION),
  FIXEXT4(EXTENSION),
  FIXEXT8(EXTENSION),
  FIXEXT16(EXTENSION),
  STR8(STRING),
  STR16(STRING),
  STR32(STRING),
  ARRAY16(ARRAY),
  ARRAY32(ARRAY),
  MAP16(MAP),
  MAP32(MAP),
  NEGFIXINT(INTEGER);

  private static final MsgPackFormat[] FORMAT_TABLE = new MsgPackFormat[256];

  static {
    // Preparing a look up table for converting byte values into MessageFormat types
    for (int b = 0; b <= 0xFF; ++b) {
      FORMAT_TABLE[b] = toMessageFormat((byte) b);
    }
  }

  protected MsgPackType type;

  MsgPackFormat(final MsgPackType type) {
    this.type = type;
  }

  public MsgPackType getType() {
    return type;
  }

  /**
   * Returns a MessageFormat type of the specified byte value
   *
   * @param b MessageFormat of the given byte
   * @return
   */
  public static MsgPackFormat valueOf(final byte b) {
    return FORMAT_TABLE[b & 0xFF];
  }

  /**
   * Converting a byte value into MessageFormat. For faster performance, use {@link #valueOf}
   *
   * @param b MessageFormat of the given byte
   * @return
   */
  static MsgPackFormat toMessageFormat(final byte b) {
    if (isPosFixInt(b)) {
      return POSFIXINT;
    }
    if (isNegFixInt(b)) {
      return NEGFIXINT;
    }
    if (isFixStr(b)) {
      return FIXSTR;
    }
    if (isFixedArray(b)) {
      return FIXARRAY;
    }
    if (isFixedMap(b)) {
      return FIXMAP;
    }
    switch (b) {
      case MsgPackCodes.NIL:
        return NIL;
      case MsgPackCodes.FALSE:
      case MsgPackCodes.TRUE:
        return BOOLEAN;
      case MsgPackCodes.BIN8:
        return BIN8;
      case MsgPackCodes.BIN16:
        return BIN16;
      case MsgPackCodes.BIN32:
        return BIN32;
      case MsgPackCodes.EXT8:
        return EXT8;
      case MsgPackCodes.EXT16:
        return EXT16;
      case MsgPackCodes.EXT32:
        return EXT32;
      case MsgPackCodes.FLOAT32:
        return FLOAT32;
      case MsgPackCodes.FLOAT64:
        return FLOAT64;
      case MsgPackCodes.UINT8:
        return UINT8;
      case MsgPackCodes.UINT16:
        return UINT16;
      case MsgPackCodes.UINT32:
        return UINT32;
      case MsgPackCodes.UINT64:
        return UINT64;
      case MsgPackCodes.INT8:
        return INT8;
      case MsgPackCodes.INT16:
        return INT16;
      case MsgPackCodes.INT32:
        return INT32;
      case MsgPackCodes.INT64:
        return INT64;
      case MsgPackCodes.FIXEXT1:
        return FIXEXT1;
      case MsgPackCodes.FIXEXT2:
        return FIXEXT2;
      case MsgPackCodes.FIXEXT4:
        return FIXEXT4;
      case MsgPackCodes.FIXEXT8:
        return FIXEXT8;
      case MsgPackCodes.FIXEXT16:
        return FIXEXT16;
      case MsgPackCodes.STR8:
        return STR8;
      case MsgPackCodes.STR16:
        return STR16;
      case MsgPackCodes.STR32:
        return STR32;
      case MsgPackCodes.ARRAY16:
        return ARRAY16;
      case MsgPackCodes.ARRAY32:
        return ARRAY32;
      case MsgPackCodes.MAP16:
        return MAP16;
      case MsgPackCodes.MAP32:
        return MAP32;
      default:
        return NEVER_USED;
    }
  }
}
