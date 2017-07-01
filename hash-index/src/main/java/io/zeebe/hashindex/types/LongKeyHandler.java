package io.zeebe.hashindex.types;

import io.zeebe.hashindex.IndexKeyHandler;
import org.agrona.BitUtil;
import org.agrona.UnsafeAccess;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class LongKeyHandler implements IndexKeyHandler
{
    private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

    public long theKey;

    @Override
    public void setKeyLength(int keyLength)
    {
        // ignore
    }

    @Override
    public int keyHashCode()
    {
        int hash = (int)theKey ^ (int)(theKey >>> 32);
        hash = hash ^ (hash >>> 16);
        return hash;
    }

    @Override
    public String toString()
    {
        return Long.valueOf(theKey).toString();
    }

    @Override
    public int getKeyLength()
    {
        return BitUtil.SIZE_OF_LONG;
    }

    @Override
    public void readKey(long keyAddr)
    {
        theKey = UNSAFE.getLong(keyAddr);
    }

    @Override
    public void writeKey(long keyAddr)
    {
        UNSAFE.putLong(keyAddr, theKey);
    }

    @Override
    public boolean keyEquals(long keyAddr)
    {
        return UNSAFE.getLong(keyAddr) == theKey;
    }
}
