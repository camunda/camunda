package io.zeebe.broker.util.msgpack.value;

import java.util.NoSuchElementException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;

public class ArrayValue<T extends BaseValue> extends BaseValue implements ArrayValueIterator<T>
{
    protected MsgPackReader elementReader = new MsgPackReader();
    protected MsgPackWriter elementWriter = new MsgPackWriter();

    protected UnsafeBuffer writeBuffer = new UnsafeBuffer(new byte[1024]);

    protected T innerValue;

    protected int length;
    protected int size;

    protected int cursor;
    protected int lastReturned;
    protected boolean skipLastReturned = false;

    public ArrayValue()
    {
        reset();
    }

    public ArrayValue(DirectBuffer defaultValue, int offset, int length)
    {
        this();

        final MsgPackReader headerReader = new MsgPackReader();
        headerReader.wrap(defaultValue, offset, length);

        this.size = headerReader.readArrayHeader();

        offset = headerReader.getOffset() - offset;
        length = length - offset;

        if (length > 0)
        {
            elementReader.wrap(defaultValue, offset, length);
        }

        this.length = length;
    }

    @Override
    public void reset()
    {
        length = 0;
        size = 0;

        cursor = 0;
        lastReturned = -1;
        skipLastReturned = false;

        elementReader.wrap(elementReader.getBuffer(), 0, 0);

        writeBuffer.setMemory(0, writeBuffer.capacity(), (byte) 0);
        elementWriter.wrap(writeBuffer, 0);

        if (innerValue != null)
        {
            innerValue.reset();
        }
    }

    public void wrapArrayValue(ArrayValue<T> from)
    {
        size = from.size;

        cursor = from.cursor;
        lastReturned = from.lastReturned;
        skipLastReturned = from.skipLastReturned;

        final int writeBufferCapacity = writeBuffer.capacity();
        final int requiredWriteBufferLength = from.elementWriter.getOffset();

        if (requiredWriteBufferLength > writeBufferCapacity)
        {
            resizeWriteBuffer(requiredWriteBufferLength);
        }

        writeBuffer.setMemory(0, writeBuffer.capacity(), (byte) 0);
        elementWriter.writeRaw(from.writeBuffer, 0, requiredWriteBufferLength);

        if (from.hasNext())
        {
            final MsgPackReader fromElementReader = from.elementReader;
            final DirectBuffer buffer = fromElementReader.getBuffer();
            final int offset = fromElementReader.getOffset();

            length = from.length - offset;
            elementReader.wrap(buffer, offset, length);
        }
        else
        {
            length = 0;
            elementReader.wrap(elementReader.getBuffer(), 0, 0);
        }
    }

    public ArrayValueIterator<T> iterator()
    {
        // reset the iterator
        cursor = 0;
        lastReturned = -1;
        skipLastReturned = false;

        final DirectBuffer buffer = elementReader.getBuffer();
        elementReader.wrap(buffer, 0, buffer.capacity());

        innerValue.reset();

        return this;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        builder.append("[");

        iterator();

        for (int i = 0; i < size; i++)
        {
            if (i > 0)
            {
                builder.append(",");
            }

            next().writeJSON(builder);
        }

        builder.append("]");
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
            final int requiredLength = innerValue.getEncodedLength() + offset;

            if (requiredLength > capacity)
            {
                resizeWriteBuffer(requiredLength);
            }

            innerValue.write(elementWriter);
        }
    }

    protected void resizeWriteBuffer(final int newLength)
    {
        final int offset = elementWriter.getOffset();
        final byte[] byteArr = new byte[newLength];

        writeBuffer.getBytes(0, byteArr, 0, offset);
        writeBuffer.wrap(byteArr, 0, newLength);
    }

    public int size()
    {
        return size;
    }

    public T getInnerValue()
    {
        return innerValue;
    }

    public void setInnerValue(T innerValue)
    {
        this.innerValue = innerValue;
    }

}
