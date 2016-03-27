package org.camunda.tngp.transport.util;

import uk.co.real_logic.agrona.BitUtil;

/**
 * Non concurrent array queue with fixed capacity.
 */
public class BoundedArrayQueue<P>
{
    protected Object[] array;

    protected int capacity;
    protected int mask;
    protected long head;
    protected long tail;

    public BoundedArrayQueue(int capacity)
    {
        if(!BitUtil.isPowerOfTwo(capacity))
        {
            throw new RuntimeException("Queue capacity must be a power of two.");
        }

        this.capacity = capacity;
        this.mask = capacity -1;

        head = tail = 0;

        array = new Object[capacity];
    }

    public boolean offer(P object)
    {
        int remainingSpace = capacity - size();

        if(remainingSpace > 0)
        {
            final int index = (int) (tail & mask);

            array[index] = object;

            ++tail;

            return true;
        }
        else
        {
            return false;
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
            array[index] = null;

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
