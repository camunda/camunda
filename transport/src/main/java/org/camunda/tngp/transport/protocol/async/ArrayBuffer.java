package org.camunda.tngp.transport.protocol.async;

import uk.co.real_logic.agrona.BitUtil;

public class ArrayBuffer<P>
{
    protected Object[] array;

    protected int capacity;
    protected int mask;
    protected long head;
    protected long tail;

    public ArrayBuffer(int capacity)
    {
        if(!BitUtil.isPowerOfTwo(capacity))
        {
            throw new RuntimeException("Pool capacity must be a power of two.");
        }

        this.capacity = capacity;
        this.mask = capacity -1;

        head = tail = 0;

        array = new Object[capacity];
    }

    public void put(P object)
    {
        int remainingSpace = capacity - size();

        if(remainingSpace > 0)
        {
            final int index = (int) (tail & mask);

            array[index] = object;

            ++tail;
        }
    }

    @SuppressWarnings("unchecked")
    public P take()
    {
        int size = size();

        Object object = null;

        if(size > 0)
        {
            final int index = (int) (head & mask);

            object = array[index];

            ++head;
        }

        return (P) object;
    }

    @SuppressWarnings("unchecked")
    public P peek()
    {
        int size = size();

        Object object = null;

        if(size > 0)
        {
            final int index = (int) (head & mask);

            object = array[index];
        }

        return (P) object;
    }

    public int size()
    {
        return (int) (tail - head);
    }

    public int getCapacity()
    {
        return capacity;
    }

}
