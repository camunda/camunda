package org.camunda.tngp.broker.util.msgpack.value;

import java.util.NoSuchElementException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;

public class ArrayValue<T extends BaseValue> extends BaseValue implements ArrayValueIterator<T>
{
    protected MsgPackReader elementReader = new MsgPackReader();
    protected MsgPackReader defaultElementReader = new MsgPackReader();
    protected MsgPackWriter elementWriter = new MsgPackWriter();

    protected UnsafeBuffer writeBuffer = new UnsafeBuffer(new byte[1024]);

    protected T innerValue;

    protected int length;
    protected int size;

    protected int cursor;
    protected int lastReturned;
    protected boolean skipLastReturned = false;

    public ArrayValue(T innerValue)
    {
        this.innerValue = innerValue;
        reset();
    }

    public ArrayValue(T innerValue, DirectBuffer defaultValue, int offset, int length)
    {
        this(innerValue);
        defaultElementReader.wrap(defaultValue, offset, length);
        read(defaultElementReader);
    }

    @Override
    public void reset()
    {
        innerValue.reset();

        length = 0;
        size = 0;

        cursor = 0;
        lastReturned = -1;
        skipLastReturned = false;

        elementReader.wrap(elementReader.getBuffer(), 0, 0);

        writeBuffer.setMemory(0, writeBuffer.capacity(), (byte) 0);
        elementWriter.wrap(writeBuffer, 0);

    }

    public void wrapReadValues(ArrayValue<T> from)
    {
        reset();

        size = from.cursor;
        cursor = from.cursor;
        lastReturned = from.lastReturned;
        skipLastReturned = from.skipLastReturned;

        final UnsafeBuffer fromWriteBuffer = from.writeBuffer;
        final int fromWriteBufferOffset = from.elementWriter.getOffset();
        elementWriter.writeRaw(fromWriteBuffer, 0, fromWriteBufferOffset);

        final MsgPackReader fromElementReader = from.elementReader;
        length = fromElementReader.getOffset();

        if (length > 0)
        {
            final int innerValueLength = from.innerValue.getEncodedLength();
            elementReader.wrap(fromElementReader.getBuffer(), length - innerValueLength, innerValueLength);
            innerValue.read(elementReader);
        }
    }

    public ArrayValueIterator<T> iterator()
    {
        return this;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        builder.append("[ size: " + size() + " ]");
    }

    @Override
    public void write(MsgPackWriter writer)
    {
        writer.writeArrayHeader(size());

        final DirectBuffer readerBuffer = elementReader.getBuffer();

        int length = this.length;

        if (size > 0)
        {
            final int writerOffset = elementWriter.getOffset();
            writer.writeRaw(writeBuffer, 0, writerOffset);

            if (!skipLastReturned && cursor > 0)
            {
                innerValue.write(writer);
            }

            if (hasNext())
            {
                final int readerOffset = elementReader.getOffset();
                length -= readerOffset;

                writer.writeRaw(readerBuffer, readerOffset, length);
            }
        }
    }

    @Override
    public void read(MsgPackReader reader)
    {
        reset();

        size = reader.readArrayHeader();

        final int offset = reader.getOffset();
        for (int i = 0; i < size; i++)
        {
            innerValue.read(reader);
        }

        final DirectBuffer buffer = reader.getBuffer();
        length = reader.getOffset() - offset;

        if (length > 0)
        {
            elementReader.wrap(buffer, offset, length);
        }

        innerValue.reset();
    }

    @Override
    public int getEncodedLength()
    {
        int length = MsgPackWriter.getEncodedArrayHeaderLenght(size());

        if (size > 0)
        {
            length += elementWriter.getOffset();

            if (!skipLastReturned && cursor > 0)
            {
                length += innerValue.getEncodedLength();
            }

            if (hasNext())
            {
                length += this.length - elementReader.getOffset();
            }
        }

        return length;
    }

    @Override
    public boolean hasNext()
    {
        return cursor < size;
    }

    @Override
    public T next()
    {
        if (cursor >= size)
        {
            throw new NoSuchElementException();
        }

        flushLastReturned();

        skipLastReturned = false;
        lastReturned = cursor;
        cursor += 1;

        innerValue.read(elementReader);
        return innerValue;
    }

    @Override
    public void remove()
    {
        if (lastReturned < 0)
        {
            throw new IllegalStateException();
        }

        if (cursor <= 0)
        {
            throw new IndexOutOfBoundsException();
        }

        size -= 1;

        skipLastReturned = true;
        cursor = lastReturned;
        lastReturned = -1;
    }

    public T add()
    {
        flushLastReturned();

        size += 1;

        skipLastReturned = false;
        cursor += 1;
        lastReturned = -1;

        innerValue.reset();
        return innerValue;
    }

    protected void flushLastReturned()
    {
        if (!skipLastReturned && cursor > 0)
        {
            final int offset = elementWriter.getOffset();
            final int capacity = writeBuffer.capacity();
            final int length = innerValue.getEncodedLength() + offset;

            if (length > capacity)
            {
                final UnsafeBuffer newWriteBuffer = new UnsafeBuffer(new byte[length]);
                newWriteBuffer.putBytes(0, writeBuffer, 0, offset);
                writeBuffer = newWriteBuffer;
                elementWriter.wrap(newWriteBuffer, offset);
            }

            innerValue.write(elementWriter);
        }
    }

    public int size()
    {
        return size;
    }

}
