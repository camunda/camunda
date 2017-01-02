package org.camunda.tngp.broker.util.msgpack;

import static org.agrona.BitUtil.*;
import static org.camunda.tngp.broker.util.msgpack.MsgPackCodes.*;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class MsgPackWriter
{
    private MutableDirectBuffer buffer;
    private int offset;

    public MsgPackWriter wrap(MutableDirectBuffer buffer, int offset)
    {
        this.buffer = buffer;
        this.offset = offset;

        return this;
    }

    public MsgPackWriter writeMapHeader(int size)
    {
        if (size < (1 << 4))
        {
            buffer.putByte(offset, (byte) (FIXMAP_PREFIX | size));
            ++offset;
        }
        else if (size < (1 << 16))
        {
            buffer.putByte(offset, MAP16);
            ++offset;

            buffer.putShort(offset, (short) size, MsgPackCodes.BYTE_ORDER);
            offset += SIZE_OF_SHORT;
        }
        else
        {
            buffer.putByte(offset, MAP32);
            ++offset;

            buffer.putInt(offset, size, MsgPackCodes.BYTE_ORDER);
            offset += SIZE_OF_INT;
        }

        return this;
    }

    public MsgPackWriter writeRaw(DirectBuffer buffer)
    {
        return writeRaw(buffer, 0, buffer.capacity());
    }

    public MsgPackWriter writeRaw(DirectBuffer buff, int offset, int length)
    {
        this.buffer.putBytes(offset, buff, offset, length);
        offset += length;

        return this;
    }

    public MsgPackWriter writeString(DirectBuffer bytes)
    {
        return this.writeString(bytes, 0, bytes.capacity());
    }

    public MsgPackWriter writeString(DirectBuffer buff, int offset, int length)
    {
        writeStringHeader(length);
        writeRaw(buff, offset, length);
        return this;
    }

    public MsgPackWriter writeLong(long v)
    {
        if (v < -(1L << 5))
        {
            if (v < -(1L << 15))
            {
                if (v < -(1L << 31))
                {
                    buffer.putByte(offset, INT64);
                    ++offset;
                    buffer.putLong(offset, v, BYTE_ORDER);
                    offset += SIZE_OF_LONG;
                }
                else
                {
                    buffer.putByte(offset, INT32);
                    ++offset;
                    buffer.putInt(offset, (int) v, BYTE_ORDER);
                    offset += SIZE_OF_INT;
                }
            }
            else
            {
                if (v < -(1 << 7))
                {
                    buffer.putByte(offset, INT16);
                    ++offset;
                    buffer.putShort(offset, (short) v, BYTE_ORDER);
                    offset += SIZE_OF_SHORT;
                }
                else
                {
                    buffer.putByte(offset, INT8);
                    ++offset;
                    buffer.putByte(offset, (byte) v);
                    ++offset;
                }
            }
        }
        else if (v < (1 << 7))
        {
            buffer.putByte(0, (byte) v);
        }
        else
        {
            if (v < (1L << 16))
            {
                if (v < (1 << 8))
                {
                    buffer.putByte(offset, UINT8);
                    ++offset;
                    buffer.putByte(offset, (byte) v);
                    ++offset;
                }
                else
                {
                    buffer.putByte(offset, UINT16);
                    ++offset;
                    buffer.putShort(offset, (short) v, BYTE_ORDER);
                    offset += SIZE_OF_SHORT;
                }
            }
            else
            {
                if (v < (1L << 32))
                {
                    buffer.putByte(offset, UINT32);
                    ++offset;
                    buffer.putInt(offset, (int) v, BYTE_ORDER);
                    offset += SIZE_OF_INT;
                }
                else
                {
                    buffer.putByte(offset, UINT64);
                    ++offset;
                    buffer.putLong(offset, v, BYTE_ORDER);
                    offset += SIZE_OF_LONG;
                }
            }
        }
        return this;
    }

    public MsgPackWriter writeStringHeader(int len)
    {
        if (len < (1 << 5))
        {
            buffer.putByte(offset, (byte) (FIXSTR_PREFIX | len));
            ++offset;
        }
        else if (len < (1 << 8))
        {
            buffer.putByte(offset, STR8);
            ++offset;

            buffer.putByte(offset, (byte) len);
            ++offset;
        }
        else if (len < (1 << 16))
        {
            buffer.putByte(offset, STR16);
            ++offset;

            buffer.putShort(offset, (short) len);
            offset += SIZE_OF_SHORT;
        }
        else
        {
            buffer.putByte(offset, STR32);
            ++offset;

            buffer.putInt(offset, len);
            offset += SIZE_OF_INT;
        }

        return this;
    }

    public MsgPackWriter writeBinary(DirectBuffer data)
    {
        return writeBinary(data, 0, data.capacity());
    }

    public MsgPackWriter writeBinary(DirectBuffer data, int offset, int length)
    {
        writeBinaryHeader(length);
        writeRaw(data, offset, length);
        return this;
    }

    public MsgPackWriter writeBinaryHeader(int len)
    {
        if (len < (1 << 8))
        {
            buffer.putByte(offset, BIN8);
            ++offset;

            buffer.putByte(offset, (byte) len);
            ++offset;
        }
        else if (len < (1 << 16))
        {
            buffer.putByte(offset, BIN16);
            ++offset;

            buffer.putShort(offset, (short) len);
            offset += SIZE_OF_SHORT;
        }
        else
        {
            buffer.putByte(offset, BIN32);
            ++offset;

            buffer.putInt(offset, len);
            offset += SIZE_OF_INT;
        }

        return this;
    }


    public MsgPackWriter writeBoolean(boolean val)
    {
        buffer.putByte(offset, val ? TRUE : FALSE);
        ++offset;

        return this;
    }

    public static int getEncodedMapHeaderLenght(int size)
    {
        int length = 1;

        if (size >= (1 << 4) && size < (1 << 16))
        {
            length += SIZE_OF_SHORT;
        }
        else
        {
            length += SIZE_OF_INT;
        }

        return length;
    }

    public static int getEncodedStringHeaderLength(int len)
    {
        int encodedLength = 1;

        if (len >= (1 << 5) && len < (1 << 8))
        {
            encodedLength++;
        }
        else if (len < (1 << 16))
        {
            encodedLength += SIZE_OF_SHORT;
        }
        else
        {
            encodedLength += SIZE_OF_INT;
        }

        return encodedLength;
    }

    public static int getEncodedStringLength(int len)
    {
        return getEncodedStringHeaderLength(len) + len;
    }

    public static int getEncodedLongValueLength(long v)
    {
        int length = 1;

        if (v < -(1L << 5))
        {
            if (v < -(1L << 15))
            {
                if (v < -(1L << 31))
                {
                    length += SIZE_OF_LONG;
                }
                else
                {
                    length += SIZE_OF_INT;
                }
            }
            else
            {
                if (v < -(1 << 7))
                {
                    length += SIZE_OF_SHORT;
                }
                else
                {
                    length += SIZE_OF_BYTE;
                }
            }
        }
        else if (v >= (1 << 7))
        {
            if (v < (1L << 16))
            {
                if (v < (1 << 8))
                {
                    length += SIZE_OF_BYTE;
                }
                else
                {
                    length += SIZE_OF_SHORT;
                }
            }
            else
            {
                if (v < (1L << 32))
                {
                    length += SIZE_OF_INT;
                }
                else
                {
                    length += SIZE_OF_LONG;
                }
            }
        }

        return length;
    }

    public static int getEncodedBooleanValueLength()
    {
        return 1;
    }

    public static int getEncodedBinaryValueLength(int len)
    {
        int encodedLength = 1;

        if (len < (1 << 8))
        {
            ++encodedLength;
        }
        else if (len < (1 << 16))
        {
            encodedLength += SIZE_OF_SHORT;
        }
        else
        {
            encodedLength += SIZE_OF_INT;
        }

        return encodedLength;
    }
}
