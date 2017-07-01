package io.zeebe.hashindex.types;

import io.zeebe.hashindex.IndexValueHandler;
import org.agrona.BitUtil;
import org.agrona.UnsafeAccess;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class LongValueHandler implements IndexValueHandler
{
    private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

    public long theValue;

    @Override
    public int getValueLength()
    {
        return BitUtil.SIZE_OF_LONG;
    }

    @Override
    public void writeValue(long writeValueAddr)
    {
        UNSAFE.putLong(writeValueAddr, theValue);
    }

    @Override
    public void readValue(long valueAddr, int valueLength)
    {
        theValue = UNSAFE.getLong(valueAddr);
    }

}