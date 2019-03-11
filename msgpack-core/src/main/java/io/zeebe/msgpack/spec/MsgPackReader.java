/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.msgpack.spec;

import static io.zeebe.msgpack.spec.MsgPackCodes.ARRAY16;
import static io.zeebe.msgpack.spec.MsgPackCodes.ARRAY32;
import static io.zeebe.msgpack.spec.MsgPackCodes.BIN16;
import static io.zeebe.msgpack.spec.MsgPackCodes.BIN32;
import static io.zeebe.msgpack.spec.MsgPackCodes.BIN8;
import static io.zeebe.msgpack.spec.MsgPackCodes.BYTE_ORDER;
import static io.zeebe.msgpack.spec.MsgPackCodes.FALSE;
import static io.zeebe.msgpack.spec.MsgPackCodes.FLOAT32;
import static io.zeebe.msgpack.spec.MsgPackCodes.FLOAT64;
import static io.zeebe.msgpack.spec.MsgPackCodes.INT16;
import static io.zeebe.msgpack.spec.MsgPackCodes.INT32;
import static io.zeebe.msgpack.spec.MsgPackCodes.INT64;
import static io.zeebe.msgpack.spec.MsgPackCodes.INT8;
import static io.zeebe.msgpack.spec.MsgPackCodes.MAP16;
import static io.zeebe.msgpack.spec.MsgPackCodes.MAP32;
import static io.zeebe.msgpack.spec.MsgPackCodes.STR16;
import static io.zeebe.msgpack.spec.MsgPackCodes.STR32;
import static io.zeebe.msgpack.spec.MsgPackCodes.STR8;
import static io.zeebe.msgpack.spec.MsgPackCodes.TRUE;
import static io.zeebe.msgpack.spec.MsgPackCodes.UINT16;
import static io.zeebe.msgpack.spec.MsgPackCodes.UINT32;
import static io.zeebe.msgpack.spec.MsgPackCodes.UINT64;
import static io.zeebe.msgpack.spec.MsgPackCodes.UINT8;
import static io.zeebe.msgpack.spec.MsgPackCodes.isFixInt;
import static io.zeebe.msgpack.spec.MsgPackCodes.isFixStr;
import static io.zeebe.msgpack.spec.MsgPackCodes.isFixedArray;
import static io.zeebe.msgpack.spec.MsgPackCodes.isFixedMap;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_SHORT;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MsgPackReader {
  public DirectBuffer buffer = new UnsafeBuffer(0, 0);
  private int offset;
  protected MsgPackToken token = new MsgPackToken();

  public MsgPackReader wrap(DirectBuffer buffer, int offset, int length) {
    this.buffer.wrap(buffer, offset, length);
    this.offset = 0;
    return this;
  }

  public void reset() {
    this.offset = 0;
  }

  public int readMapHeader() {
    final byte headerByte = buffer.getByte(offset);
    ++offset;

    final int mapSize;

    if (isFixedMap(headerByte)) {
      mapSize = headerByte & (byte) 0x0F;
    } else {
      switch (headerByte) {
        case MAP16:
          mapSize = buffer.getShort(offset, BYTE_ORDER) & 0xffff;
          offset += SIZE_OF_SHORT;
          break;

        case MAP32:
          mapSize = (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
          offset += SIZE_OF_INT;
          break;

        default:
          throw exceptionOnUnknownHeader("map", headerByte);
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
        case ARRAY16:
          mapSize = buffer.getShort(offset, BYTE_ORDER) & 0xffff;
          offset += SIZE_OF_SHORT;
          break;

        case ARRAY32:
          mapSize = (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
          offset += SIZE_OF_INT;
          break;

        default:
          throw exceptionOnUnknownHeader("array", headerByte);
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
        case STR8:
          stringLength = buffer.getByte(offset) & 0xff;
          ++offset;
          break;

        case STR16:
          stringLength = buffer.getShort(offset, BYTE_ORDER) & 0xffff;
          offset += SIZE_OF_SHORT;
          break;

        case STR32:
          stringLength = (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
          offset += SIZE_OF_INT;
          break;

        default:
          throw exceptionOnUnknownHeader("string", headerByte);
      }
    }
    return stringLength;
  }

  public int readBinaryLength() {
    final int length;

    final byte headerByte = buffer.getByte(offset);
    ++offset;

    switch (headerByte) {
      case BIN8:
        length = buffer.getByte(offset) & 0xff;
        ++offset;
        break;

      case BIN16:
        length = buffer.getShort(offset, BYTE_ORDER) & 0xffff;
        offset += SIZE_OF_SHORT;
        break;

      case BIN32:
        length = (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
        offset += SIZE_OF_INT;
        break;

      default:
        throw exceptionOnUnknownHeader("binary", headerByte);
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
        case UINT8:
          val = buffer.getByte(offset) & 0xffL;
          ++offset;
          break;

        case UINT16:
          val = buffer.getShort(offset, BYTE_ORDER) & 0xffffL;
          offset += 2;
          break;

        case UINT32:
          val = buffer.getInt(offset, BYTE_ORDER) & 0xffff_ffffL;
          offset += 4;
          break;

        case UINT64:
          val = ensurePositive(buffer.getLong(offset, BYTE_ORDER));
          offset += 8;
          break;

        case INT8:
          val = buffer.getByte(offset);
          ++offset;
          break;

        case INT16:
          val = buffer.getShort(offset, BYTE_ORDER);
          offset += 2;
          break;

        case INT32:
          val = buffer.getInt(offset, BYTE_ORDER);
          offset += 4;
          break;

        case INT64:
          val = buffer.getLong(offset, BYTE_ORDER);
          offset += 8;
          break;

        default:
          throw exceptionOnUnknownHeader("long", headerByte);
      }
    }

    return val;
  }

  /**
   * Float is the term in the msgpack spec for all values represented by Java types float and double
   *
   * @return the value
   */
  public strictfp double readFloat() {
    final byte headerByte = buffer.getByte(offset);
    ++offset;
    final double value;

    switch (headerByte) {
      case FLOAT32:
        value = buffer.getFloat(offset, BYTE_ORDER);
        offset += 4;
        break;
      case FLOAT64:
        value = buffer.getDouble(offset, BYTE_ORDER);
        offset += 8;
        break;
      default:
        throw exceptionOnUnknownHeader("float", headerByte);
    }

    return value;
  }

  public boolean readBoolean() {
    final byte headerByte = buffer.getByte(offset);
    ++offset;

    final boolean theBool;

    switch (headerByte) {
      case TRUE:
        theBool = true;
        break;

      case FALSE:
        theBool = false;
        break;

      default:
        throw exceptionOnUnknownHeader("boolean", headerByte);
    }

    return theBool;
  }

  public MsgPackToken readToken() {
    final byte b = buffer.getByte(offset);
    final MsgPackFormat format = MsgPackFormat.valueOf(b);

    final int currentOffset = offset;

    switch (format.type) {
      case INTEGER:
        token.setType(MsgPackType.INTEGER);
        token.setValue(readInteger());
        break;
      case FLOAT:
        token.setType(MsgPackType.FLOAT);
        token.setValue(readFloat());
        break;
      case BOOLEAN:
        token.setType(MsgPackType.BOOLEAN);
        token.setValue(readBoolean());
        break;
      case MAP:
        token.setType(MsgPackType.MAP);
        token.setMapHeader(readMapHeader());
        break;
      case ARRAY:
        token.setType(MsgPackType.ARRAY);
        token.setArrayHeader(readArrayHeader());
        break;
      case NIL:
        token.setType(MsgPackType.NIL);
        skipValue();
        break;
      case BINARY:
        token.setType(MsgPackType.BINARY);
        final int binaryLength = readBinaryLength();
        token.setValue(buffer, offset, binaryLength);
        skipBytes(binaryLength);
        break;
      case STRING:
        token.setType(MsgPackType.STRING);
        final int stringLength = readStringLength();
        token.setValue(buffer, offset, stringLength);
        skipBytes(stringLength);
        break;
      case EXTENSION:
      case NEVER_USED:
        throw new MsgpackReaderException(
            String.format("Unknown token format '%s'", format.getType().name()));
    }

    token.setTotalLength(offset - currentOffset);

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
        case POSFIXINT:
        case NEGFIXINT:
        case BOOLEAN:
        case NIL:
          break;
        case FIXMAP:
          {
            final int mapLen = b & 0x0f;
            count += mapLen * 2L;
            break;
          }
        case FIXARRAY:
          {
            final int arrayLen = b & 0x0f;
            count += arrayLen;
            break;
          }
        case FIXSTR:
          {
            final int strLen = b & 0x1f;
            offset += strLen;
            break;
          }
        case INT8:
        case UINT8:
          ++offset;
          break;
        case INT16:
        case UINT16:
          offset += 2;
          break;
        case INT32:
        case UINT32:
        case FLOAT32:
          offset += 4;
          break;
        case INT64:
        case UINT64:
        case FLOAT64:
          offset += 8;
          break;
        case BIN8:
        case STR8:
          offset += 1 + Byte.toUnsignedInt(buffer.getByte(offset));
          break;
        case BIN16:
        case STR16:
          offset += 2 + Short.toUnsignedInt(buffer.getShort(offset, BYTE_ORDER));
          break;
        case BIN32:
        case STR32:
          offset += 4 + (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
          break;
        case FIXEXT1:
          offset += 2;
          break;
        case FIXEXT2:
          offset += 3;
          break;
        case FIXEXT4:
          offset += 5;
          break;
        case FIXEXT8:
          offset += 9;
          break;
        case FIXEXT16:
          offset += 17;
          break;
        case EXT8:
          offset += 1 + 1 + Byte.toUnsignedInt(buffer.getByte(offset));
          break;
        case EXT16:
          offset += 1 + 2 + Short.toUnsignedInt(buffer.getShort(offset, BYTE_ORDER));
          break;
        case EXT32:
          offset += 1 + 4 + (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
          break;
        case ARRAY16:
          count += Short.toUnsignedInt(buffer.getShort(offset, BYTE_ORDER));
          offset += 2;
          break;
        case ARRAY32:
          count += ensurePositive(buffer.getInt(offset, BYTE_ORDER));
          offset += 4;
          break;
        case MAP16:
          count += Short.toUnsignedInt(buffer.getShort(offset, BYTE_ORDER)) * 2L;
          offset += 2;
          break;
        case MAP32:
          count += ensurePositive(buffer.getInt(offset, BYTE_ORDER)) * 2L;
          offset += 4;
          break;
        case NEVER_USED:
          throw new MsgpackReaderException("Encountered 0xC1 \"NEVER_USED\" byte");
      }

      count--;
    }
  }

  public void skipBytes(int stringLength) {
    offset += stringLength;
  }

  public boolean hasNext() {
    return offset < buffer.capacity();
  }

  protected MsgpackReaderException exceptionOnUnknownHeader(
      final String name, final byte headerByte) {
    return new MsgpackReaderException(
        String.format(
            "Unable to determine %s type, found unknown header byte 0x%02x at reader offset %d",
            name, headerByte, offset - 1));
  }

  private long ensurePositive(long size) {
    try {
      return MsgPackHelper.ensurePositive(size);
    } catch (MsgpackException e) {
      throw new MsgpackReaderException(e);
    }
  }
}
