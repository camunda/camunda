package io.zeebe.transport.util;

import java.lang.reflect.Array;

import org.agrona.BitUtil;

/**
 * Array-based data structure holding a fixed number of objects,
 * assigning a unique long key to each object.
 *
 * Supports a single reader/writer for all mutating operations,
 * ie. {@link LongArrayIndex#put(Object)}, {@link LongArrayIndex#remove(long, Object)} and {@link LongArrayIndex#reset()}.
 *
 * Can be iterated concurrently by readers who read the size field prior
 * to iteration. The readers are then guaranteed to see the array's state
 * corresponding to the size they read or more.
 */
public class LongArrayIndex<T>
{
    protected final T[] indexedObjects;

    protected final long[] keys;

    protected final int mask;

    protected final int capacity;

    protected volatile int size;

    @SuppressWarnings("unchecked")
    public LongArrayIndex(final int capacity, Class<T> elementClass)
    {
        if (!BitUtil.isPowerOfTwo(capacity))
        {
            throw new RuntimeException("Pool capacity must be a power of two.");
        }

        indexedObjects = (T[]) Array.newInstance(elementClass, capacity);
        keys = new long[capacity];
        this.capacity = capacity;
        this.mask = capacity - 1;

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
     * Puts an object into the array returning
     *
     * @param object stuff to put
     *
     * @return the key under which it can be retrieved.
     */
    public long put(final T object)
    {
        int slot = -1;
        long key = -1;

        if (size < capacity)
        {
            for (int i = 0; i < capacity; i++)
            {
                if (indexedObjects[i] == null)
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
     *
     * @param key key of value to select
     * @param objectToRemove expected value at key
     *
     * @return the value
     */
    public T remove(final long key, final T objectToRemove)
    {
        final int index = (int) (key & mask);
        final T object = indexedObjects[index];
        if (object == objectToRemove)
        {
            indexedObjects[index] = null;
            size--;  // volatile store
            return object;
        }
        else
        {
            return null;
        }
    }

    /**
     * Retrieve without removing the object for the given index
     *
     * @param key the key
     *
     * @return the value
     */
    public T poll(final long key)
    {
        if (size > 0) // volatile load
        {
            final int index = (int) (key & mask);
            return indexedObjects[index];
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

    public T[] getObjects()
    {
        return indexedObjects;
    }
}
