package org.camunda.tngp.example.msgpack.impl;

import org.agrona.BitUtil;
import org.agrona.concurrent.UnsafeBuffer;

public class ImmutableIntList
{
    protected UnsafeBuffer buffer = new UnsafeBuffer(0, 0);
    protected int size = 0;

    public ImmutableIntList(int maxCapacity)
    {
        buffer.wrap(new byte[maxCapacity * BitUtil.SIZE_OF_INT]);
    }

    public int getSize()
    {
        return size;
    }

    public void add(int element)
    {
        buffer.putInt(size * BitUtil.SIZE_OF_INT, element);
        size++;
    }

    public void removeLast()
    {
        size--;
    }

    public int get(int index)
    {
        // TODO: could also become an iterator
        // TODO: bounds check
        return buffer.getInt(index * BitUtil.SIZE_OF_INT);
    }

    public void clear()
    {
        size = 0;
    }

}
