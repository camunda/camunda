/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gossip;

import org.agrona.collections.Hashing;
import org.agrona.generation.DoNotSub;

import java.io.Serializable;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * A {@link List} implementation that stores int values with the ability to not have them boxed.
 *
 * NOTE: this is fixed version of the agrona implementation an should be removed after a patch was released
 *       see https://github.com/real-logic/agrona/pull/125
 */
public final class IntArrayList extends AbstractList<Integer> implements List<Integer>, RandomAccess, Serializable
{
    /**
     * The default value that will be used in place of null for an element.
     */
    public static final int DEFAULT_NULL_VALUE = Integer.MIN_VALUE;

    /**
     * Initial capacity to which the array will be sized.
     */
    @DoNotSub public static final int INITIAL_CAPACITY = 10;

    /**
     * Maximum capacity to which the array can grow.
     */
    @DoNotSub public static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;

    private final int nullValue;
    @DoNotSub private int size = 0;
    private int[] elements;

    public IntArrayList()
    {
        this(INITIAL_CAPACITY, DEFAULT_NULL_VALUE);
    }

    /**
     * Construct a new list.
     *
     * @param initialCapacity for the backing array.
     * @param nullValue       to be used to represent a null element.
     */
    public IntArrayList(
        @DoNotSub final int initialCapacity,
        final int nullValue)
    {
        this.nullValue = nullValue;
        elements = new int[Math.max(initialCapacity, INITIAL_CAPACITY)];
    }

    /**
     * Create a new list that wraps an existing arrays without copying it.
     *
     * @param initialElements to be wrapped.
     * @param initialSize     of the array to wrap.
     * @param nullValue       to be used to represent a null element.
     */
    public IntArrayList(
        final int[] initialElements,
        @DoNotSub final int initialSize,
        final int nullValue)
    {
        wrap(initialElements, initialSize);
        this.nullValue = nullValue;
    }

    /**
     * Wrap an existing array without copying it.
     * <p>
     * The array length must be greater than or equal to {@link #INITIAL_CAPACITY}.
     *
     * @param initialElements to be wrapped.
     * @param initialSize     of the array to wrap.
     * @throws IllegalArgumentException if the initialSize is is less than {@link #INITIAL_CAPACITY} or greater than
     * the length of the initial array.
     */
    public void wrap(
        final int[] initialElements,
        @DoNotSub final int initialSize)
    {
        if (initialSize < 0 || initialSize > initialElements.length)
        {
            throw new IllegalArgumentException(
                "Illegal initial size " + initialSize + " for array length of " + initialElements.length);
        }

        if (initialElements.length < INITIAL_CAPACITY)
        {
            throw new IllegalArgumentException(
                "Illegal initial array length " + initialElements.length + ", minimum required is " + INITIAL_CAPACITY);
        }

        elements = initialElements;
        size = initialSize;
    }

    /**
     * The value representing a null element.
     *
     * @return value representing a null element.
     */
    public int nullValue()
    {
        return nullValue;
    }

    @DoNotSub public int size()
    {
        return size;
    }

    public void clear()
    {
        size = 0;
    }

    /**
     * Trim the underlying array to be the current size, or {@link #INITIAL_CAPACITY} if size is less.
     */
    public void trimToSize()
    {
        if (elements.length != size && elements.length > INITIAL_CAPACITY)
        {
            elements = Arrays.copyOf(elements, Math.max(INITIAL_CAPACITY, size));
        }
    }

    public Integer get(
        @DoNotSub final int index)
    {
        final int value = getInt(index);

        return value == nullValue ? null : value;
    }

    /**
     * Get the element at a given index without boxing.
     *
     * @param index to get.
     * @return the unboxed element.
     */
    public int getInt(
        @DoNotSub final int index)
    {
        checkIndex(index);

        return elements[index];
    }

    public boolean add(final Integer element)
    {
        return addInt(null == element ? nullValue : element);
    }

    /**
     * Add an element without boxing.
     *
     * @param element to be added.
     * @return true
     */
    public boolean addInt(final int element)
    {
        ensureCapacityPrivate(size + 1);

        elements[size] = element;
        size++;

        return true;
    }

    public void add(
        @DoNotSub final int index,
        final Integer element)
    {
        addInt(index, null == element ? nullValue : element);
    }

    /**
     * Add a element without boxing at a given index.
     *
     * @param index   at which the element should be added.
     * @param element to be added.
     */
    public void addInt(
        @DoNotSub final int index,
        final int element)
    {
        checkIndexForAdd(index);

        @DoNotSub final int requiredSize = size + 1;
        ensureCapacityPrivate(requiredSize);

        if (index < size)
        {
            System.arraycopy(elements, index, elements, index + 1, size - index);
        }

        elements[index] = element;
        size++;
    }

    public Integer set(
        @DoNotSub final int index,
        final Integer element)
    {
        final int previous = setInt(index, null == element ? nullValue : element);

        return nullValue == previous ? null : previous;
    }

    /**
     * Set an element at a given index without boxing.
     *
     * @param index   at which to set the element.
     * @param element to be added.
     * @return the previous element at the index.
     */
    public int setInt(
        @DoNotSub final int index,
        final int element)
    {
        checkIndex(index);

        final int previous = elements[index];
        elements[index] = element;

        return previous;
    }

    /**
     * Does the list contain this element value.
     *
     * @param value of the element.
     * @return true if present otherwise false.
     */
    public boolean containsInt(final int value)
    {
        return -1 != indexOf(value);
    }

    /**
     * Index of the first element with this value.
     *
     * @param value for the element.
     * @return the index if found otherwise -1.
     */
    @DoNotSub public int indexOf(
        final int value)
    {
        for (@DoNotSub int i = 0; i < size; i++)
        {
            if (value == elements[i])
            {
                return i;
            }
        }

        return -1;
    }

    /**
     * Index of the last element with this value.
     *
     * @param value for the element.
     * @return the index if found otherwise -1.
     */
    @DoNotSub public int lastIndexOf(
        final int value)
    {
        for (@DoNotSub int i = size - 1; i >= 0; i--)
        {
            if (value == elements[i])
            {
                return i;
            }
        }

        return -1;
    }

    /**
     * Remove at a given index.
     *
     * @param index of the element to be removed.
     * @return the existing value at this index.
     */
    public Integer remove(
        @DoNotSub final int index)
    {
        checkIndex(index);

        final int value = elements[index];

        @DoNotSub final int moveCount = size - index - 1;
        if (moveCount > 0)
        {
            System.arraycopy(elements, index + 1, elements, index, moveCount);
        }

        size--;

        return value;
    }

    /**
     * Remove the first instance of a value if found in the list.
     *
     * @param value to be removed.
     * @return true if successful otherwise false.
     */
    public boolean removeInt(final int value)
    {
        @DoNotSub final int index = indexOf(value);
        if (-1 != index)
        {
            remove(index);

            return true;
        }

        return false;
    }

    /**
     * Push an element onto the end of the array like a stack.
     *
     * @param element to be pushed onto the end of the array.
     */
    public void pushInt(final int element)
    {
        ensureCapacityPrivate(size + 1);

        elements[size] = element;
        size++;
    }

    /**
     * Pop a value off the end of the array as a stack operation.
     *
     * @return the value at the end of the array.
     * @throws NoSuchElementException if the array is empty.
     */
    public int popInt()
    {
        if (isEmpty())
        {
            throw new NoSuchElementException();
        }

        return elements[--size];
    }

    /**
     * For each element in order provide the int value to a {@link IntConsumer}.
     *
     * @param consumer for each element.
     */
    public void forEachOrderedInt(final IntConsumer consumer)
    {
        for (@DoNotSub int i = 0; i < size; i++)
        {
            consumer.accept(elements[i]);
        }
    }

    /**
     * Create a {@link IntStream} over the elements of underlying array.
     *
     * @return a {@link IntStream} over the elements of underlying array.
     */
    public IntStream intStream()
    {
        return Arrays.stream(elements, 0, size);
    }

    /**
     * Create a new array that is a copy of the elements.
     *
     * @return a copy of the elements.
     */
    public int[] toIntArray()
    {
        return Arrays.copyOf(elements, size);
    }

    /**
     * Create a new array that is a copy of the elements.
     *
     * @param dst destination array for the copy if it is the correct size.
     * @return a copy of the elements.
     */
    public int[] toIntArray(final int[] dst)
    {
        if (dst.length == size)
        {
            System.arraycopy(elements, 0, dst, 0, dst.length);
            return dst;
        }
        else
        {
            return Arrays.copyOf(elements, size);
        }
    }

    /**
     * Ensure the backing array has a required capacity.
     *
     * @param requiredCapacity for the backing array.
     */
    public void ensureCapacity(@DoNotSub final int requiredCapacity)
    {
        ensureCapacityPrivate(Math.max(requiredCapacity, INITIAL_CAPACITY));
    }

    public boolean equals(final IntArrayList that)
    {
        if (that == this)
        {
            return true;
        }

        boolean isEqual = false;

        if (this.size == that.size)
        {
            isEqual = true;

            for (@DoNotSub int i = 0; i < size; i++)
            {
                final int thisValue = this.elements[i];
                final int thatValue = that.elements[i];

                if (thisValue != thatValue)
                {
                    if (thisValue != this.nullValue || thatValue != that.nullValue)
                    {
                        isEqual = false;
                        break;
                    }
                }
            }
        }

        return isEqual;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(final Object other)
    {
        if (other == this)
        {
            return true;
        }

        boolean isEqual = false;

        if (other instanceof IntArrayList)
        {
            return equals((IntArrayList)other);
        }
        else if (other instanceof List)
        {
            final List<?> that = (List<?>)other;

            if (this.size == ((List)other).size())
            {
                isEqual = true;
                @DoNotSub int i = 0;

                for (final Object o : that)
                {
                    if (o == null || o instanceof Integer)
                    {
                        final Integer thisValue = get(i++);
                        final Integer thatValue = (Integer)o;

                        if (Objects.equals(thisValue, thatValue))
                        {
                            continue;
                        }
                    }

                    isEqual = false;
                    break;
                }
            }
        }

        return isEqual;
    }

    /**
     * {@inheritDoc}
     */
    @DoNotSub public int hashCode()
    {
        @DoNotSub int hashCode = 0;
        for (@DoNotSub int i = 0; i < size; i++)
        {
            final int value = elements[i];

            hashCode = 31 * hashCode + (value == nullValue ? 0 : Hashing.hash(value));
        }

        return hashCode;
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append('[');

        for (@DoNotSub int i = 0; i < size; i++)
        {
            final int value = elements[i];
            if (value != nullValue)
            {
                sb.append(value);
                sb.append(", ");
            }
        }

        if (sb.length() > 1)
        {
            sb.setLength(sb.length() - 2);
        }

        sb.append(']');

        return sb.toString();
    }

    private void ensureCapacityPrivate(@DoNotSub final int requiredCapacity)
    {
        @DoNotSub final int currentCapacity = elements.length;
        if (requiredCapacity > currentCapacity)
        {
            @DoNotSub int newCapacity = currentCapacity + (currentCapacity >> 1);

            if (newCapacity < 0 || newCapacity > MAX_CAPACITY)
            {
                if (currentCapacity == MAX_CAPACITY)
                {
                    throw new IllegalStateException("Max capacity reached: " + MAX_CAPACITY);
                }

                newCapacity = MAX_CAPACITY;
            }

            final int[] newElements = new int[newCapacity];
            System.arraycopy(elements, 0, newElements, 0, currentCapacity);
            elements = newElements;
        }
    }

    private void checkIndex(@DoNotSub final int index)
    {
        if (index >= size || index < 0)
        {
            throw new IndexOutOfBoundsException("index=" + index + " size=" + size);
        }
    }

    private void checkIndexForAdd(@DoNotSub final int index)
    {
        if (index > size || index < 0)
        {
            throw new IndexOutOfBoundsException("index=" + index + " size=" + size);
        }
    }
}
