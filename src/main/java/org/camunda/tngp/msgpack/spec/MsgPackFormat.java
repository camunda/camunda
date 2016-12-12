package org.camunda.tngp.msgpack.spec;

public enum MsgPackFormat
{
    FIXSTR(MsgPackType.STRING, 0xa0, 0xe0),
    STR_8(MsgPackType.STRING, 0xd9, 0xff),
    STR_16(MsgPackType.STRING, 0xda, 0xff),
    STR_32(MsgPackType.STRING, 0xdb, 0xff),
    FIXMAP(MsgPackType.MAP, 0x80, 0xf0),
    MAP_16(MsgPackType.MAP, 0xde, 0xff),
    MAP_32(MsgPackType.MAP, 0xdf, 0xff),
    FIXARR(MsgPackType.ARRAY, 0x90, 0xf0),
    ARRAY_16(MsgPackType.ARRAY, 0xdc, 0xff),
    ARRAY_32(MsgPackType.ARRAY, 0xdd, 0xff),
    NIL(MsgPackType.NIL, 0xc0, 0xff),
    BOOLEAN_TRUE(MsgPackType.BOOLEAN, 0xc2, 0xff),
    BOOLEAN_FALSE(MsgPackType.BOOLEAN, 0xc3, 0xff),
    FIXNUM_POSITIVE(MsgPackType.INTEGER, 0x00, 0x80),
    FIXNUM_NEGATIVE(MsgPackType.INTEGER, 0xd0, 0xd0),
    UINT_8(MsgPackType.INTEGER, 0xcc, 0xff),
    UINT_16(MsgPackType.INTEGER, 0xcd, 0xff),
    UINT_32(MsgPackType.INTEGER, 0xce, 0xff),
    UINT_64(MsgPackType.INTEGER, 0xcf, 0xff),
    FLOAT_32(MsgPackType.INTEGER, 0xca, 0xff),
    FLOAT_64(MsgPackType.INTEGER, 0xcb, 0xff);

    protected MsgPackType type;
    protected int prefix;
    protected int bitmask;

    MsgPackFormat(MsgPackType type, int prefix, int bitmask)
    {
        this.type = type;
        this.prefix = prefix;
        this.bitmask = bitmask;
    }

    public boolean applies(byte formatPrefix)
    {
        return (formatPrefix & 0xff & bitmask) == prefix;
    }

    public MsgPackType getType()
    {
        return type;
    }

    public static MsgPackFormat getFormat(byte formatByte)
    {
        final MsgPackFormat[] values = values();

        for (int i = 0; i < values.length; i++)
        {
            if (values[i].applies(formatByte))
            {
                return values[i];
            }
        }

        return null;
    }

}
