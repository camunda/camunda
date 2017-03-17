package org.camunda.tngp.util.collection;

import java.util.Arrays;

/**
 * Does currently not perform validity checks for the operations. Is not thread-safe.
 */
public class LongRingBuffer
{
    protected final long[] elements;

    protected int capacity;
    protected int size;
    protected int head; // points to the position of the last added element
    protected int tail; // points to the position of that last consumed element

    public LongRingBuffer(int capacity)
    {
        elements = new long[capacity];
        Arrays.fill(elements, -1);
        head = -1;
        tail = -1;
        this.capacity = capacity;
        size = 0;
    }

    /**
     * @return true if no more elements can be added
     */
    public boolean isSaturated()
    {
        return size == capacity;
    }

    /**
     * Consumes until the given element or the head is reached
     *
     * @param element
     */
    public void consumeUntilInclusive(long element)
    {
        if (size > 0)
        {
            do
            {
                tail = (tail + 1) % elements.length;
                size--;
            }
            while (size > 0 && elements[tail] != element);
        }
    }

    /**
     * Adds element at head position; does not perform bounds check
     *
     * @param element to add
     */
    public void addElementToHead(long element)
    {
        head = (head + 1) % elements.length;
        elements[head] = element;
        size++;
    }


}
