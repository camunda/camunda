/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.spec;

import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.ARRAY16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.ARRAY32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.BIN16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.BIN32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.BIN8;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.BYTE_ORDER;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.FALSE;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.FLOAT32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.FLOAT64;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.INT16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.INT32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.INT64;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.INT8;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.MAP16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.MAP32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.STR16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.STR32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.STR8;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.TRUE;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.UINT16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.UINT32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.UINT64;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.UINT8;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.isFixInt;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.isFixStr;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.isFixedArray;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.isFixedMap;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_SHORT;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MsgPackReader {
  private final MsgPackToken token = new MsgPackToken();
  private final DirectBuffer buffer = new UnsafeBuffer(0, 0);
  private int offset;

  public MsgPackReader wrap(final DirectBuffer buffer, final int offset, final int length) {
    this.buffer.wrap(buffer, offset, length);
    this.offset = 0;
    return this;
  }

  public void reset() {
    offset = 0;
  }

  public int readMapHeader() {
    final byte headerByte = buffer.getByte(offset);
    ++offset;

    final int mapSize;

    if (isFixedMap(headerByte)) {
      mapSize = headerByte & (byte) 0x0F;
    } else {
      switch (headerByte) {
        case MAP16 -> {
          mapSize = buffer.getShort(offset, BYTE_ORDER) & 0xffff;
          offset += SIZE_OF_SHORT;
        }
        case MAP32 -> {
          mapSize = (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
          offset += SIZE_OF_INT;
        }
        default -> throw exceptionOnUnknownHeader("map", headerByte);
      }
    }

    return mapSize;
  }

  public int readArrayHeader() {
    final byte headerByte = buffer.getByte(offset);
    ++offset;

    final int mapSize;

    if (isFixedArray(headerByte)) {
      mapSize = headerByte & (byte) 0x0F;
    } else {
      switch (headerByte) {
        case ARRAY16 -> {
          mapSize = buffer.getShort(offset, BYTE_ORDER) & 0xffff;
          offset += SIZE_OF_SHORT;
        }
        case ARRAY32 -> {
          mapSize = (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
          offset += SIZE_OF_INT;
        }
        default -> throw exceptionOnUnknownHeader("array", headerByte);
      }
    }

    return mapSize;
  }

  public int readStringLength() {
    final byte headerByte = buffer.getByte(offset);
    ++offset;

    final int stringLength;

    if (isFixStr(headerByte)) {
      stringLength = headerByte & (byte) 0x1F;
    } else {
      switch (headerByte) {
        case STR8 -> {
          stringLength = buffer.getByte(offset) & 0xff;
          ++offset;
        }
        case STR16 -> {
          stringLength = buffer.getShort(offset, BYTE_ORDER) & 0xffff;
          offset += SIZE_OF_SHORT;
        }
        case STR32 -> {
          stringLength = (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
          offset += SIZE_OF_INT;
        }
        default -> throw exceptionOnUnknownHeader("string", headerByte);
      }
    }
    return stringLength;
  }

  public int readBinaryLength() {
    final int length;

    final byte headerByte = buffer.getByte(offset);
    ++offset;

    switch (headerByte) {
      case BIN8 -> {
        length = buffer.getByte(offset) & 0xff;
        ++offset;
      }
      case BIN16 -> {
        length = buffer.getShort(offset, BYTE_ORDER) & 0xffff;
        offset += SIZE_OF_SHORT;
      }
      case BIN32 -> {
        length = (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
        offset += SIZE_OF_INT;
      }
      default -> throw exceptionOnUnknownHeader("binary", headerByte);
    }

    return length;
  }

  /**
   * Integer is the term of the msgpack spec for all natural numbers
   *
   * @return the value
   */
  public long readInteger() {
    final byte headerByte = buffer.getByte(offset);
    ++offset;

    final long val;

    if (isFixInt(headerByte)) {
      val = headerByte;
    } else {
      switch (headerByte) {
        case UINT8 -> {
          val = buffer.getByte(offset) & 0xffL;
          ++offset;
        }
        case UINT16 -> {
          val = buffer.getShort(offset, BYTE_ORDER) & 0xffffL;
          offset += 2;
        }
        case UINT32 -> {
          val = buffer.getInt(offset, BYTE_ORDER) & 0xffff_ffffL;
          offset += 4;
        }
        case UINT64 -> {
          val = ensurePositive(buffer.getLong(offset, BYTE_ORDER));
          offset += 8;
        }
        case INT8 -> {
          val = buffer.getByte(offset);
          ++offset;
        }
        case INT16 -> {
          val = buffer.getShort(offset, BYTE_ORDER);
          offset += 2;
        }
        case INT32 -> {
          val = buffer.getInt(offset, BYTE_ORDER);
          offset += 4;
        }
        case INT64 -> {
          val = buffer.getLong(offset, BYTE_ORDER);
          offset += 8;
        }
        default -> throw exceptionOnUnknownHeader("long", headerByte);
      }
    }

    return val;
  }

  /**
   * Float is the term in the msgpack spec for all values represented by Java types float and double
   *
   * @return the value
   */
  public double readFloat() {
    final byte headerByte = buffer.getByte(offset);
    ++offset;
    final double value;

    switch (headerByte) {
      case FLOAT32 -> {
        value = buffer.getFloat(offset, BYTE_ORDER);
        offset += 4;
      }
      case FLOAT64 -> {
        value = buffer.getDouble(offset, BYTE_ORDER);
        offset += 8;
      }
      default -> throw exceptionOnUnknownHeader("float", headerByte);
    }

    return value;
  }

  public boolean readBoolean() {
    final byte headerByte = buffer.getByte(offset);
    ++offset;

    return switch (headerByte) {
      case TRUE -> true;
      case FALSE -> false;
      default -> throw exceptionOnUnknownHeader("boolean", headerByte);
    };
  }

  public MsgPackToken readToken() {
    final byte b = buffer.getByte(offset);
    final MsgPackFormat format = MsgPackFormat.valueOf(b);

    switch (format.type) {
      case INTEGER -> {
        token.setType(MsgPackType.INTEGER);
        token.setValue(readInteger());
      }
      case FLOAT -> {
        token.setType(MsgPackType.FLOAT);
        token.setValue(readFloat());
      }
      case BOOLEAN -> {
        token.setType(MsgPackType.BOOLEAN);
        token.setValue(readBoolean());
      }
      case MAP -> {
        token.setType(MsgPackType.MAP);
        token.setMapHeader(readMapHeader());
      }
      case ARRAY -> {
        token.setType(MsgPackType.ARRAY);
        token.setArrayHeader(readArrayHeader());
      }
      case NIL -> {
        token.setType(MsgPackType.NIL);
        skipValue();
      }
      case BINARY -> {
        token.setType(MsgPackType.BINARY);
        final int binaryLength = readBinaryLength();
        token.setValue(buffer, offset, binaryLength);
        skipBytes(binaryLength);
      }
      case STRING -> {
        token.setType(MsgPackType.STRING);
        final int stringLength = readStringLength();
        token.setValue(buffer, offset, stringLength);
        skipBytes(stringLength);
      }
      default ->
          throw new MsgpackReaderException(
              String.format("Unknown token format '%s'", format.getType().name()));
    }

    return token;
  }

  public DirectBuffer getBuffer() {
    return buffer;
  }

  public int getOffset() {
    return offset;
  }

  public void skipValue() {
    skipValues(1);
  }

  public void skipValues(long count) {
    while (count > 0) {
      final byte b = buffer.getByte(offset);
      ++offset;

      final MsgPackFormat f = MsgPackFormat.valueOf(b);

      switch (f) {
        case POSFIXINT, NEGFIXINT, BOOLEAN, NIL -> {}
        case FIXMAP -> {
          final int mapLen = b & 0x0f;
          count += mapLen * 2L;
        }
        case FIXARRAY -> {
          final int arrayLen = b & 0x0f;
          count += arrayLen;
        }
        case FIXSTR -> {
          final int strLen = b & 0x1f;
          offset += strLen;
        }
        case INT8, UINT8 -> ++offset;
        case INT16, UINT16 -> offset += 2;
        case INT32, UINT32, FLOAT32 -> offset += 4;
        case INT64, UINT64, FLOAT64 -> offset += 8;
        case BIN8, STR8 -> offset += 1 + Byte.toUnsignedInt(buffer.getByte(offset));
        case BIN16, STR16 -> offset += 2 + Short.toUnsignedInt(buffer.getShort(offset, BYTE_ORDER));
        case BIN32, STR32 -> offset += 4 + (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
        case FIXEXT1 -> offset += 2;
        case FIXEXT2 -> offset += 3;
        case FIXEXT4 -> offset += 5;
        case FIXEXT8 -> offset += 9;
        case FIXEXT16 -> offset += 17;
        case EXT8 -> offset += 1 + 1 + Byte.toUnsignedInt(buffer.getByte(offset));
        case EXT16 -> offset += 1 + 2 + Short.toUnsignedInt(buffer.getShort(offset, BYTE_ORDER));
        case EXT32 -> offset += 1 + 4 + (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
        case ARRAY16 -> {
          count += Short.toUnsignedInt(buffer.getShort(offset, BYTE_ORDER));
          offset += 2;
        }
        case ARRAY32 -> {
          count += ensurePositive(buffer.getInt(offset, BYTE_ORDER));
          offset += 4;
        }
        case MAP16 -> {
          count += Short.toUnsignedInt(buffer.getShort(offset, BYTE_ORDER)) * 2L;
          offset += 2;
        }
        case MAP32 -> {
          count += ensurePositive(buffer.getInt(offset, BYTE_ORDER)) * 2L;
          offset += 4;
        }
        default -> throw new MsgpackReaderException("Encountered 0xC1 \"NEVER_USED\" byte");
      }

      count--;
    }
  }

  public void skipBytes(final int stringLength) {
    offset += stringLength;
  }

  public boolean hasNext() {
    return offset < buffer.capacity();
  }

  private MsgpackReaderException exceptionOnUnknownHeader(
      final String name, final byte headerByte) {
    return new MsgpackReaderException(
        String.format(
            "Unable to determine %s type, found unknown header byte 0x%02x at reader offset %d",
            name, headerByte, offset - 1));
  }

  private long ensurePositive(final long size) {
    try {
      return MsgPackHelper.ensurePositive(size);
    } catch (final MsgpackException e) {
      throw new MsgpackReaderException(e);
    }
  }
}
