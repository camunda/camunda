package org.camunda.tngp.broker.test.util;

import java.util.Arrays;

import org.assertj.core.api.AbstractAssert;

import org.agrona.DirectBuffer;

public class BufferAssert extends AbstractAssert<BufferAssert, DirectBuffer>
{

    protected BufferAssert(DirectBuffer actual)
    {
        super(actual, BufferAssert.class);
    }

    public static BufferAssert assertThatBuffer(DirectBuffer buffer)
    {
        return new BufferAssert(buffer);
    }

    public BufferAssert hasBytes(byte[] expected, int position)
    {
        isNotNull();

        final byte[] actualBytes = new byte[expected.length];

        // TODO: try-catch in case buffer has not expected size
        actual.getBytes(position, actualBytes, 0, actualBytes.length);

        if (!Arrays.equals(expected, actualBytes))
        {
            failWithMessage("Expected byte array does not match bytes in buffer");
        }

        return this;
    }

    public BufferAssert hasBytes(byte[] expected)
    {
        return hasBytes(expected, 0);
    }

    public BufferAssert hasCapacity(int expectedCapacity)
    {
        isNotNull();

        if (expectedCapacity != actual.capacity())
        {
            failWithMessage("Expected capacity " + expectedCapacity + " but was " + actual.capacity());
        }

        return this;
    }

}
