package io.zeebe.transport.util;

import org.agrona.MutableDirectBuffer;

import io.zeebe.util.buffer.BufferWriter;

public class FailingBufferWriter implements BufferWriter
{
    @Override
    public int getLength()
    {
        return 10;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        throw new FailingBufferWriterException("Could not write - expected");
    }

    public static class FailingBufferWriterException extends RuntimeException
    {

        private static final long serialVersionUID = 1L;

        public FailingBufferWriterException(String string)
        {
            super(string);
        }
    }
}
