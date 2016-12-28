package org.camunda.tngp.broker.taskqueue.processor.stuff;

import java.util.Arrays;

import org.agrona.BitUtil;

public class VarLengthField implements DataStuff
{
    public int length = 0;
    public byte[] bytes = new byte[0];

    public void reset()
    {
        if (length > 0)
        {
            Arrays.fill(bytes, (byte) 0);
        }

        length = 0;
    }

    public void get(int lengh, BytesGetter source)
    {
        if (bytes.length < lengh)
        {
            bytes = new byte[lengh];
        }

        if (lengh > 0)
        {
            source.getInto(bytes, 0, length);
        }

        this.length = lengh;
    }

    public void put(BytesSetter target)
    {
        target.putFrom(bytes, 0, length);
    }

    @Override
    public int getLength()
    {
        return BitUtil.SIZE_OF_SHORT + length;
    }

    @FunctionalInterface
    public interface BytesGetter
    {
        void getInto(byte[] target, int offset, int length);
    }

    @FunctionalInterface
    public interface BytesSetter
    {
        void putFrom(byte[] target, int offset, int length);
    }

}
