package org.camunda.tngp.transport.util;

import uk.co.real_logic.agrona.BitUtil;

/**
 * Array-based data structure holding a fixed number of objects,
 * assigning a unique long key to each object.
 *
 * Supports a single reader/writer for all mutating operations,
 * ie. {@link #put(Object)}, {@link #remove(long)} and {{@link #reset()}.
 *
 * Can be iterated concurrently by readers who read the size field prior
 * to iteration. The readers are then guaranteed to see the array's state
 * corresponding to the size they read or more.
 */
public class LongArrayIndex<T>
{
    protected final Object[] indexedObjects;

    protected final long[] keys;

    protected final int mask;

    protected final int capacity;

    protected volatile int size;

    public LongArrayIndex(int capacity)
    {
        if(!BitUtil.isPowerOfTwo(capacity))
        {
            throw new RuntimeException("Pool capacity must be a power of two.");
        }

        indexedObjects = new Object[capacity];
        keys = new long[capacity];
        this.capacity = capacity;
        this.mask = capacity -1;

        // assign initial keys.
        for (int i = 0; i < capacity; i++)
        {
            keys[i] = i;
        }

        reset();
    }

    public void reset()
    {
        for (int i = 0; i < capacity; i++)
        {
            indexedObjects[i] = null;
            // keys are not reset in order to not give out the same key twice even if the index is reused.
        }
        size = 0; // volatile store
    }

    /**
     * Puts an object into the array returning the key under which it
     * can be retrieved.
     */
    public long put(T object)
    {
        int slot = -1;
        long key = -1;

        if(size < capacity)
        {
            for (int i = 0; i < capacity; i++)
            {
                if(indexedObjects[i] == null)
                {
                    slot = i;
                    break;
                }
            }

            indexedObjects[slot] = object;
            key = keys[slot];
            keys[slot] = key + capacity;
            size++;  // volatile store
        }
        return key;
    }

    /**
     * Remove the object for the given key if that object is still referenced
     * by the array.
     */
    @SuppressWarnings("unchecked")
    public T remove(long key, T objectToRemove)
    {
        int index = (int) (key & mask);
        Object object = indexedObjects[index];
        if(object == objectToRemove)
        {
            indexedObjects[index] = null;
            size--;  // volatile store
            return (T) object;
        }
        else
        {
            return null;
        }
    }

    /**
     * Retrieve without removing the object for the given index
     */
    @SuppressWarnings("unchecked")
    public T poll(long key)
    {
        if(size > 0) // volatile load
        {
            int index = (int) (key & mask);
            return (T) indexedObjects[index];
        }
        else
        {
            return null;
        }
    }

    public int size()
    {
        return size; // volatile load
    }

    @SuppressWarnings("unchecked")
    public T[] getObjects()
    {
        return (T[]) indexedObjects;
    }
}
