package org.camunda.tngp.test.util;

import static org.camunda.tngp.util.buffer.BufferUtil.*;

import java.util.Arrays;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.AbstractAssert;
import org.camunda.tngp.util.buffer.BufferWriter;


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

        try
        {
            actual.getBytes(position, actualBytes, 0, actualBytes.length);
        }
        catch (final Exception e)
        {
            e.printStackTrace();
            failWithMessage("Unable to read %d bytes from actual: %s", actualBytes.length, e.getMessage());
        }

        if (!Arrays.equals(expected, actualBytes))
        {
            failWithMessage("Expected byte array match bytes <%s> but was <%s>", bytesAsHexString(expected, NO_WRAP), bytesAsHexString(actualBytes, NO_WRAP));
        }

        return this;
    }

    public BufferAssert hasBytes(byte[] expected)
    {
        return hasBytes(expected, 0);
    }

    public BufferAssert hasBytes(DirectBuffer buffer, int offset, int length)
    {
        final byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes);
        return hasBytes(bytes);
    }

    public BufferAssert hasBytes(DirectBuffer buffer)
    {
        return hasBytes(buffer, 0, buffer.capacity());
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

    public BufferAssert hasBytes(final BufferWriter writer)
    {
        final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[writer.getLength()]);
        writer.write(buffer, 0);

        return hasBytes(buffer);
    }

}
