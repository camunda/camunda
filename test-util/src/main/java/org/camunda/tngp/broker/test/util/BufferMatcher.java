package org.camunda.tngp.broker.test.util;

import java.util.Arrays;

import org.mockito.ArgumentMatcher;

import org.agrona.DirectBuffer;

public class BufferMatcher extends ArgumentMatcher<DirectBuffer>
{
    protected byte[] expectedBytes;
    protected int position = 0;

    @Override
    public boolean matches(Object argument)
    {
        if (argument == null || !(argument instanceof DirectBuffer))
        {
            return false;
        }

        final byte[] actualBytes = new byte[expectedBytes.length];

        final DirectBuffer buffer = (DirectBuffer) argument;

        // TODO: try-catch in case buffer has not expected size
        buffer.getBytes(position, actualBytes, 0, actualBytes.length);

        return Arrays.equals(expectedBytes, actualBytes);
    }

    public static BufferMatcher hasBytes(byte[] bytes)
    {
        final BufferMatcher matcher = new BufferMatcher();

        matcher.expectedBytes = bytes;

        return matcher;
    }

    public BufferMatcher atPosition(int position)
    {
        this.position = position;
        return this;
    }

}
