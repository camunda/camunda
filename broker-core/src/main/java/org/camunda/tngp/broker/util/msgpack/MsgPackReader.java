package org.camunda.tngp.broker.util.msgpack;

import static org.agrona.BitUtil.*;
import static org.camunda.tngp.broker.util.msgpack.MsgPackCodes.*;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MsgPackReader
{
    private DirectBuffer buffer = new UnsafeBuffer(0, 0);
    private int offset;

    public MsgPackReader wrap(DirectBuffer buffer, int offset, int length)
    {
        this.buffer.wrap(buffer, offset, length);
        this.offset = 0;
        return this;
    }

    public int readMapHeader()
    {
        final byte mapHeaderByte = buffer.getByte(offset);
        ++offset;

        final int mapSize;

        if (isFixedMap(mapHeaderByte))
        {
            mapSize = mapHeaderByte & (byte) 0x0F;
        }
        else
        {
            switch (mapHeaderByte)
            {
                case MAP16:
                    mapSize = buffer.getShort(offset, BYTE_ORDER);
                    offset += SIZE_OF_SHORT;
                    break;

                case MAP32:
                    mapSize = buffer.getInt(offset, BYTE_ORDER);
                    offset += SIZE_OF_INT;
                    break;

                default:
                    throw new RuntimeException("Not a map");
            }
        }

        return mapSize;
    }

    public int readStringLength()
    {
        final byte headerByte = buffer.getByte(offset);
        ++offset;

        final int stringLength;

        if (isFixStr(headerByte))
        {
            stringLength = headerByte & (byte) 0x1F;
        }
        else
        {
            switch (headerByte)
            {
                case STR8:
                    stringLength = buffer.getByte(offset);
                    ++offset;
                    break;

                case STR16:
                    stringLength = buffer.getShort(offset, BYTE_ORDER);
                    offset += SIZE_OF_SHORT;
                    break;

                case STR32:
                    stringLength = buffer.getInt(offset, BYTE_ORDER);
                    offset += SIZE_OF_INT;
                    break;

                default:
                    throw new RuntimeException("Not a string");
            }
        }
        return stringLength;
    }

    public int readBinaryLength()
    {
        final int length;

        final byte headerByte = buffer.getByte(offset);
        ++offset;

        switch (headerByte)
        {
            case BIN8:
                length = buffer.getByte(offset);
                ++offset;
                break;

            case BIN16:
                length = buffer.getShort(offset, BYTE_ORDER);
                offset += SIZE_OF_SHORT;
                break;

            case BIN32:
                length = buffer.getInt(offset, BYTE_ORDER);
                offset += SIZE_OF_INT;
                break;

            default:
                throw new RuntimeException("Not binary");
        }

        return length;
    }

    public long readLong()
    {
        final byte b = buffer.getByte(offset);
        ++offset;

        final long val;

        if (isFixInt(b))
        {
            val = b;
        }
        else
        {
            switch (b)
            {
                case UINT8:
                    val = buffer.getByte(offset);
                    ++offset;
                    break;

                case UINT16:
                    val = buffer.getShort(offset, BYTE_ORDER);
                    offset += 2;
                    break;

                case UINT32:
                    val = buffer.getInt(offset, BYTE_ORDER);
                    offset += 4;
                    break;

                case UINT64:
                    val = buffer.getLong(offset, BYTE_ORDER);
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
                    throw new RuntimeException("Not a long.");
            }
        }

        return val;
    }

    public boolean readBoolean()
    {
        final byte b = buffer.getByte(offset);
        ++offset;

        final boolean theBool;

        switch (b)
        {
            case TRUE:
                theBool = true;
                break;

            case FALSE:
                theBool = false;
                break;

            default:
                throw new RuntimeException("Not a boolean value");
        }

        return theBool;
    }

    public DirectBuffer getBuffer()
    {
        return buffer;
    }

    public int getOffset()
    {
        return offset;
    }

    public void skipValue()
    {
        skipValues(1);
    }

    public void skipValues(int count)
    {
        while (count > 0)
        {
            final byte b = buffer.getByte(offset);
            ++offset;

            final MsgPackType f = MsgPackType.valueOf(b);

            switch (f)
            {
                case POSFIXINT:
                case NEGFIXINT:
                case BOOLEAN:
                case NIL:
                    break;
                case FIXMAP: {
                    final int mapLen = b & 0x0f;
                    count += mapLen * 2;
                    break;
                }
                case FIXARRAY: {
                    final int arrayLen = b & 0x0f;
                    count += arrayLen;
                    break;
                }
                case FIXSTR: {
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
                    offset += 1 + buffer.getByte(offset);
                    break;
                case BIN16:
                case STR16:
                    offset += 2 + buffer.getShort(offset);
                    break;
                case BIN32:
                case STR32:
                    offset += 4 + buffer.getInt(offset);
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
                    offset += 1 + 1 + buffer.getByte(offset);
                    break;
                case EXT16:
                    offset += 1 + 2 + buffer.getShort(offset);
                    break;
                case EXT32:
                    offset += 1 + 4 + buffer.getInt(offset);
                    break;
                case ARRAY16:
                    count += buffer.getShort(offset);
                    offset += 2;
                    break;
                case ARRAY32:
                    count += buffer.getInt(offset);
                    offset += 4;
                    break;
                case MAP16:
                    count += buffer.getShort(offset) * 2;
                    offset += 2;
                    break;
                case MAP32:
                    count += buffer.getInt(offset) * 2;
                    offset += 4;
                    break;
                case NEVER_USED:
                    throw new RuntimeException("Encountered 0xC1 \"NEVER_USED\" byte");
            }

            count--;
        }
    }

    public void skipBytes(int stringLength)
    {
        offset += stringLength;
    }

}
