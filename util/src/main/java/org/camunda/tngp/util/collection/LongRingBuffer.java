package org.camunda.tngp.util.collection;

import java.util.Arrays;
import java.util.function.LongConsumer;

import org.agrona.BitUtil;

/**
 * Thread-safe in a one-consumer, one-producer setting
 */
public class LongRingBuffer
{
    protected final long[] elements;

    protected final int capacity;
    protected final int bufferCapacity;
    protected volatile long head; // points to the position of the last added element
    protected volatile long tail; // points to the position of that last consumed element

    public LongRingBuffer(int capacity)
    {
        /*
         * For ease of implementation, the actual capacity must be a power of 2; this allows
         * easy remainder calculations, etc.
         */
        if (BitUtil.isPowerOfTwo(capacity))
        {
            this.bufferCapacity = capacity;
        }
        else
        {
            this.bufferCapacity = BitUtil.findNextPositivePowerOfTwo(capacity);
        }

        elements = new long[bufferCapacity];
        Arrays.fill(elements, -1);
        head = -1;
        tail = -1;
        this.capacity = capacity;
    }

    /**
     * @return true if no more elements can be added
     */
    public boolean isSaturated()
    {
        return size() == capacity;
    }

    /**
     * Consumes until an element equal or greater than the argument (inclusive) or the head is reached
     *
     * @param element
     */
    public void consumeAscendingUntilInclusive(long element)
    {
        while (head != tail && elements[mapToBufferIndex(tail + 1, bufferCapacity)] <= element)
        {
            tail++;
        }
    }

    public int consume(LongConsumer consumer)
    {
        return consume(consumer, Integer.MAX_VALUE);
    }

    public int consume(LongConsumer consumer, int maxElements)
    {
        int elementCounter = 0;
        while (head != tail && elementCounter < maxElements)
        {
            final long nextElement = elements[mapToBufferIndex(tail + 1, bufferCapacity)];
            consumer.accept(nextElement);
            tail++;
            elementCounter++;
        }
        return elementCounter;
    }

    protected static int mapToBufferIndex(long indexCounter, int bufferCapacity)
    {
        // using long index pointers and downcasting avoids negative values once indexCounter flows over
        return (int) ((indexCounter) & (bufferCapacity - 1));
    }

    public int size()
    {
        return (int) (head - tail);
    }

    /**
     * Adds element at head position
     *
     * @param element to add
     */
    public boolean addElementToHead(long element)
    {
        if (size() == capacity)
        {
            return false;
        }

        elements[mapToBufferIndex(head + 1, bufferCapacity)] = element;
        head++;
        return true;
    }


}
