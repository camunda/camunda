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
     * Consumes until an element equal or greater than the argument (inclusive) or the head is reached
     *
     * @param element
     */
    public void consumeAscendingUntilInclusive(long element)
    {
        while (size > 0 && elements[(tail + 1) % elements.length] <= element)
        {
            tail = (tail + 1) % elements.length;
            size--;
        }
    }

    public int size()
    {
        return size;
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
