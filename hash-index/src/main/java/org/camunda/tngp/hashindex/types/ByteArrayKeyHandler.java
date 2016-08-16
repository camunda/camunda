package org.camunda.tngp.hashindex.types;

import java.util.Arrays;

import org.camunda.tngp.hashindex.IndexKeyHandler;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class ByteArrayKeyHandler implements IndexKeyHandler
{
    public byte[] theKey;
    public int keyLength;

    public void setKey(byte[] key)
    {
        System.arraycopy(key, 0, this.theKey, 0, key.length);
        if (key.length < this.keyLength)
        {
            Arrays.fill(this.theKey, key.length, this.keyLength, (byte) 0);
        }
    }

    public void setKey(DirectBuffer buffer, int offset, int length)
    {
        buffer.getBytes(offset, this.theKey, 0, length);
        if (length < this.keyLength)
        {
            Arrays.fill(this.theKey, length, this.keyLength, (byte) 0);
        }
    }

    @Override
    public void setKeyLength(int keyLength)
    {
        this.keyLength = keyLength;
        this.theKey = new byte[keyLength];
    }

    @Override
    public int keyHashCode()
    {
        int result = 1;

        for (int i = 0; i < keyLength; i++)
        {
            result = 31 * result + theKey[i];
        }

        return result;
    }

    @Override
    public void readKey(MutableDirectBuffer buffer, int recordKeyOffset)
    {
        buffer.getBytes(recordKeyOffset, theKey, 0, keyLength);
    }

    @Override
    public void writeKey(MutableDirectBuffer buffer, int recordKeyOffset)
    {
        buffer.putBytes(recordKeyOffset, theKey, 0, keyLength);
    }

    @Override
    public boolean keyEquals(DirectBuffer buffer, int offset)
    {
        for (int i = 0; i < keyLength; i++)
        {
            if (theKey[i] != buffer.getByte(offset + i))
            {
                return false;
            }
        }
        return true;
    }

}
