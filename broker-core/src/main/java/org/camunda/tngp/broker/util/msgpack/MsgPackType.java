package org.camunda.tngp.broker.util.msgpack;

import static org.camunda.tngp.broker.util.msgpack.MsgPackCodes.*;

/**
 * Describes the list of the message format types defined in the MessagePack specification.
 */
public enum MsgPackType
{
    // INT7
    POSFIXINT,
    // MAP4
    FIXMAP,
    // ARRAY4
    FIXARRAY,
    // STR5
    FIXSTR,
    NIL,
    NEVER_USED,
    BOOLEAN,
    BIN8,
    BIN16,
    BIN32,
    EXT8,
    EXT16,
    EXT32,
    FLOAT32,
    FLOAT64,
    UINT8,
    UINT16,
    UINT32,
    UINT64,

    INT8,
    INT16,
    INT32,
    INT64,
    FIXEXT1,
    FIXEXT2,
    FIXEXT4,
    FIXEXT8,
    FIXEXT16,
    STR8,
    STR16,
    STR32,
    ARRAY16,
    ARRAY32,
    MAP16,
    MAP32,
    NEGFIXINT;

    private static final MsgPackType[] FORMAT_TABLE = new MsgPackType[256];

    static
    {
        // Preparing a look up table for converting byte values into MessageFormat types
        for (int b = 0; b <= 0xFF; ++b)
        {
            FORMAT_TABLE[b] = toMessageFormat((byte) b);
        }
    }

    /**
     * Returns a MessageFormat type of the specified byte value
     *
     * @param b MessageFormat of the given byte
     * @return
     */
    public static MsgPackType valueOf(final byte b)
    {
        return FORMAT_TABLE[b & 0xFF];
    }

    /**
     * Converting a byte value into MessageFormat. For faster performance, use {@link #valueOf}
     *
     * @param b MessageFormat of the given byte
     * @return
     */
    static MsgPackType toMessageFormat(final byte b)
    {
        if (isPosFixInt(b))
        {
            return POSFIXINT;
        }
        if (isNegFixInt(b))
        {
            return NEGFIXINT;
        }
        if (isFixStr(b))
        {
            return FIXSTR;
        }
        if (isFixedArray(b))
        {
            return FIXARRAY;
        }
        if (isFixedMap(b))
        {
            return FIXMAP;
        }
        switch (b)
        {
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