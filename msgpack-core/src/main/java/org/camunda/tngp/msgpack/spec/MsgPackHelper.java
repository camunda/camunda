package org.camunda.tngp.msgpack.spec;

public class MsgPackHelper
{
    public static final byte[] EMTPY_OBJECT = new byte[]{ MsgPackCodes.FIXMAP_PREFIX };
    public static final byte[] EMPTY_ARRAY = new byte[]{ MsgPackCodes.FIXARRAY_PREFIX };
    public static final int ensurePositiveSize(int size)
    {
        if (size < 0)
        {
            System.out.println("Negative buffer size");
            throw new RuntimeException("Negative buffer size");
        }
        else
        {
            return size;
        }

    }
}
