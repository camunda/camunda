package org.camunda.tngp.msgpack.spec;

import static org.agrona.BitUtil.*;
import static org.camunda.tngp.msgpack.spec.MsgPackCodes.*;
import static org.camunda.tngp.msgpack.spec.MsgPackHelper.ensurePositive;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MsgPackReader
{
    private DirectBuffer buffer = new UnsafeBuffer(0, 0);
    private int offset;
    protected MsgPackToken token = new MsgPackToken();

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
                    mapSize = buffer.getShort(offset, BYTE_ORDER) & 0xffff;
                    offset += SIZE_OF_SHORT;
                    break;

                case MAP32:
                    mapSize = (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
                    offset += SIZE_OF_INT;
                    break;

                default:
                    throw new RuntimeException("Not a map");
            }
        }

        return mapSize;
    }

    public int readArrayHeader()
    {
        final byte headerByte = buffer.getByte(offset);
        ++offset;

        final int mapSize;

        if (isFixedArray(headerByte))
        {
            mapSize = headerByte & (byte) 0x0F;
        }
        else
        {
            switch (headerByte)
            {
                case ARRAY16:
                    mapSize = buffer.getShort(offset, BYTE_ORDER) & 0xffff;
                    offset += SIZE_OF_SHORT;
                    break;

                case ARRAY32:
                    mapSize = (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
                    offset += SIZE_OF_INT;
                    break;

                default:
                    throw new RuntimeException("Not an array");
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
                length = (int) ensurePositive(buffer.getInt(offset, BYTE_ORDER));
                offset += SIZE_OF_INT;
                break;

            default:
                throw new RuntimeException("Not binary");
        }

        return length;
    }

    /**
     * Integer is the term of the msgpack spec for all natural numbers
     * @return the value
     */
    public long readInteger()
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
                    throw new RuntimeException("Not a long.");
            }
        }

        return val;
    }

    /**
     * Float is the term in the msgpack spec for all values represented by Java types float and double
     * @return the value
     */
    public strictfp double readFloat()
    {
        final byte b = buffer.getByte(offset);
        ++offset;
        final double value;

        switch (b)
        {
            case FLOAT32:
                value = buffer.getFloat(offset, BYTE_ORDER);
                offset += 4;
                break;
            case FLOAT64:
                value = buffer.getDouble(offset, BYTE_ORDER);
                offset += 8;
                break;
            default:
                throw new RuntimeException("Not a float");
        }

        return value;
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

    public MsgPackToken readToken()
    {
        final byte b = buffer.getByte(offset);
        final MsgPackFormat format = MsgPackFormat.valueOf(b);

        final int currentOffset = offset;

        switch (format.type)
        {
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
                throw new RuntimeException("Unsupported token format");
        }

        token.setTotalLength(offset - currentOffset);

        return token;

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

            final MsgPackFormat f = MsgPackFormat.valueOf(b);

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
                    offset += 2 + buffer.getShort(offset, BYTE_ORDER);
                    break;
                case BIN32:
                case STR32:
                    offset += 4 + buffer.getInt(offset, BYTE_ORDER);
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
                    offset += 1 + 2 + buffer.getShort(offset, BYTE_ORDER);
                    break;
                case EXT32:
                    offset += 1 + 4 + buffer.getInt(offset, BYTE_ORDER);
                    break;
                case ARRAY16:
                    count += buffer.getShort(offset, BYTE_ORDER);
                    offset += 2;
                    break;
                case ARRAY32:
                    count += buffer.getInt(offset, BYTE_ORDER);
                    offset += 4;
                    break;
                case MAP16:
                    count += buffer.getShort(offset, BYTE_ORDER) * 2;
                    offset += 2;
                    break;
                case MAP32:
                    count += buffer.getInt(offset, BYTE_ORDER) * 2;
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

    public boolean hasNext()
    {
        return offset < buffer.capacity();
    }

}
