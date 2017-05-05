package org.camunda.tngp.transport.impl;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public class GrowablePool<T> implements Iterable<T>
{
    protected final Class<T> elementClass;
    protected T[] values;

    protected final Predicate<T> removalPredicate;
    protected final ToIntFunction<PoolIterator<T>> evictionFunction;
    protected final Consumer<T> evictionHandler;
    protected final GrowablePoolIterator iterator = new GrowablePoolIterator();

    public GrowablePool(
            Class<T> elementClass,
            int initialCapacity,
            Predicate<T> removalPredicate,
            ToIntFunction<PoolIterator<T>> evictionFunction,
            Consumer<T> evictionHandler)
    {
        this.elementClass = elementClass;
        this.values = (T[]) Array.newInstance(elementClass, initialCapacity);
        this.removalPredicate = removalPredicate;
        this.evictionFunction = evictionFunction;
        this.evictionHandler = evictionHandler;
    }

    protected void grow()
    {
        final T[] newValues = (T[]) Array.newInstance(elementClass, this.values.length * 2);
        System.arraycopy(values, 0, newValues, 0, values.length);
        this.values = newValues;
    }

    public void add(T element)
    {
        // try to reuse an empty existing slot
        for (int i = 0; i < values.length; i++)
        {
            if (tryRemove(i) == null)
            {
                values[i] = element;
                return;
            }
        }

        final int currentCapacity = values.length;

        // try to evict an element
        final int indexToEvict = evictionFunction.applyAsInt(iterator());

        final int indexToAddTo;
        if (indexToEvict >= 0)
        {
            final T valueToEvict = values[indexToEvict];
            evictionHandler.accept(valueToEvict);
            indexToAddTo = indexToEvict;
        }
        else
        {
            // expand list
            grow();
            indexToAddTo = currentCapacity;
        }

        values[indexToAddTo] = element;
    }

    public void remove(T element)
    {

        int elementIndex = -1;

        for (int i = 0; i < values.length; i++)
        {
            if (values[i] == element)
            {
                elementIndex = i;
            }
        }
        if (elementIndex >= 0)
        {
            dropElement(elementIndex);
        }
    }

    protected void dropElement(int index)
    {
        values[index] = null;
    }

    protected T tryRemove(int index)
    {
        final T result = values[index];

        if (result != null && removalPredicate.test(result))
        {
            dropElement(index);
            return null;
        }
        else
        {
            return result;
        }
    }

    public GrowablePoolIterator iterator()
    {
        iterator.reset();
        return iterator;
    }

    protected interface PoolIterator<T> extends Iterator<T>
    {
        int getCurrentElementIndex();
    }

    protected class GrowablePoolIterator implements PoolIterator<T>
    {
        protected int currentElementIndex = -1;
        protected int nextElementIndex = -1;

        @Override
        public boolean hasNext()
        {
            return nextElementIndex < values.length;
        }

        public void reset()
        {
            nextElementIndex = -1;
            moveToNext();
        }

        @Override
        public T next()
        {
            final T nextElement = values[nextElementIndex];
            currentElementIndex = nextElementIndex;
            moveToNext();
            return nextElement;
        }

        @Override
        public int getCurrentElementIndex()
        {
            return currentElementIndex;
        }

        protected void moveToNext()
        {
            do
            {
                nextElementIndex++;

            }
            while (nextElementIndex < values.length && tryRemove(nextElementIndex) == null);
        }
    }
}
