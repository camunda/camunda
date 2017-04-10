package org.camunda.tngp.msgpack.spec;

public class MsgPackHelper
{
    public static final byte[] EMTPY_OBJECT = new byte[]{ MsgPackCodes.FIXMAP_PREFIX };
    public static final byte[] EMPTY_ARRAY = new byte[]{ MsgPackCodes.FIXARRAY_PREFIX };
    public static final long ensurePositive(long size)
    {
        if (size < 0)
        {
            throw new RuntimeException("Negative value should not be accepted by size value and unsiged 64bit integer");
        }
        else
        {
            return size;
        }

    }
}
