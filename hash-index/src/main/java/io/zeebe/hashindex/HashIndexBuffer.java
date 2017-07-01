package io.zeebe.hashindex;

import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.BufferUtil.ARRAY_BASE_OFFSET;

import java.io.*;

import org.agrona.UnsafeAccess;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class HashIndexBuffer implements Closeable
{
    private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

    private final int length;
    private final long addr;

    public HashIndexBuffer(int indexSize)
    {
        length = indexSize * SIZE_OF_LONG;
        addr = UNSAFE.allocateMemory(length);
        clear();
    }

    @Override
    public void close() throws IOException
    {
        UNSAFE.freeMemory(addr);
    }

    public long getBlockOffset(int blockId)
    {
        return UNSAFE.getLong(addr + (blockId * SIZE_OF_LONG));
    }

    public void setBlockOffset(int blockId, long offset)
    {
        UNSAFE.putLong(addr + (blockId * SIZE_OF_LONG), offset);
    }

    public void clear()
    {
        UNSAFE.setMemory(addr, length, (byte) 0);
    }

    public void writeToStream(OutputStream outputStream, byte[] buffer) throws IOException
    {
        for (int offset = 0; offset < length; offset += buffer.length)
        {
            final int copyLength = Math.min(buffer.length, length - offset);
            UNSAFE.copyMemory(null, addr + offset, buffer, ARRAY_BASE_OFFSET, copyLength);
            outputStream.write(buffer, 0, copyLength);
        }
    }

    public void readFromStream(InputStream inputStream, byte[] buffer) throws IOException
    {
        for (int offset = 0; offset < length; offset += buffer.length)
        {
            final int readLength = Math.min(buffer.length, length - offset);
            inputStream.read(buffer, 0, readLength);
            UNSAFE.copyMemory(buffer, ARRAY_BASE_OFFSET, null, addr + offset, readLength);
        }
    }
}
