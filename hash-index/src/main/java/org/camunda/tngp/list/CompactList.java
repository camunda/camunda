package org.camunda.tngp.list;

import static java.lang.Math.max;
import static org.camunda.tngp.list.CompactListDescriptor.capacityOffset;
import static org.camunda.tngp.list.CompactListDescriptor.elementDataOffset;
import static org.camunda.tngp.list.CompactListDescriptor.elementLengthOffset;
import static org.camunda.tngp.list.CompactListDescriptor.elementMaxLengthOffset;
import static org.camunda.tngp.list.CompactListDescriptor.elementOffset;
import static org.camunda.tngp.list.CompactListDescriptor.framedLength;
import static org.camunda.tngp.list.CompactListDescriptor.requiredBufferCapacity;
import static org.camunda.tngp.list.CompactListDescriptor.sizeOffset;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.function.Function;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Compact, off-heap list datastructure
 */
public class CompactList implements Iterable<MutableDirectBuffer>
{
    protected final UnsafeBuffer listBuffer = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer elementBuffer = new UnsafeBuffer(0, 0);

    protected final int framedElementLength;

    protected final CompactListIterator iterator;

    public CompactList(int elementMaxLength, int capacity, Function<Integer, ByteBuffer> bufferAllocator)
    {
        this(bufferAllocator.apply(requiredBufferCapacity(framedLength(elementMaxLength), capacity)), elementMaxLength, capacity);
    }

    public CompactList(ByteBuffer underlyingBuffer, int elementMaxLength, int capacity)
    {
        framedElementLength = framedLength(elementMaxLength);
        final int requiredBufferCapacity = requiredBufferCapacity(framedElementLength, capacity);
        final int bufferCapacity = underlyingBuffer.capacity();

        if (bufferCapacity < requiredBufferCapacity)
        {
            final String errorMessage = String.format("Not enough capacity in provided buffer. Has %d, required %d", bufferCapacity, requiredBufferCapacity);
            throw new IllegalArgumentException(errorMessage);
        }

        listBuffer.wrap(underlyingBuffer, 0, requiredBufferCapacity);

        // write header
        listBuffer.putInt(sizeOffset(), 0);
        listBuffer.putInt(elementMaxLengthOffset(), elementMaxLength);
        listBuffer.putInt(capacityOffset(), capacity);

        iterator = new CompactListIterator(this);
    }

    /**
     * Appends the passed element to the end of the list.
     *
     * @param buffer to which the view is attached.
     */
    public void add(DirectBuffer buffer)
    {
        add(buffer, 0, buffer.capacity());
    }

    /**
     * Appends the passed element to the end of the list.
     *
     * @param buffer to which the element is attached.
     * @param offset at which the element begins.
     * @param length of the element.
     */
    public void add(DirectBuffer buffer, int offset, int length)
    {
        add(buffer, offset, length, max(size(), 0));
    }

    /**
     * Adds the passed element at the given index.
     *
     * @param buffer to which the element is attached.
     * @param offset at which the element begins.
     * @param length of the element.
     * @param idx at which position the element should be added.
     */
    public void add(DirectBuffer buffer, int offset, int length, int idx)
    {
        final int size = size();
        final int capacity = capacity();

        elementLengthCheck(length);
        boundsCheck(idx, size);

        if (capacity == size)
        {
            final String errorMessage = String.format("Cannot add element: list is full. Capacity=%d", capacity);
            throw new IllegalArgumentException(errorMessage);
        }

        final int elementOffset = elementOffset(framedElementLength, idx);

        if (size - idx > 0)
        {
            final int copyOffset = elementOffset + framedElementLength;
            final int copyLength = (size - idx) * framedElementLength;

            listBuffer.putBytes(copyOffset, listBuffer, elementOffset, copyLength);
        }

        setValue(buffer, offset, length, idx, elementOffset);
        setSize(size + 1);
    }

    /**
     * Replaces the element at the given index with the passed element
     *
     * @param idx of the element to replace
     * @param buffer from which the element bytes will be copied.
     */
    public void set(int idx, DirectBuffer buffer)
    {
        set(idx, buffer, 0, buffer.capacity());
    }

    /**
     * Replaces the element at the given index with the passed element
     *
     * @param idx of the element to replace
     * @param buffer from which the element bytes will be copied.
     * @param offset at which the element begins.
     * @param length of the element.
     */
    public void set(int idx, DirectBuffer buffer, int offset, int length)
    {
        final int size = size();

        elementLengthCheck(length);
        boundsCheckIncludingSize(idx, size);

        setValue(buffer, offset, length, idx);
    }

    /**
     * Removes an element at the passed index.
     *
     * @param idx to remove the corresponding element.
     */
    public void remove(int idx)
    {
        final int size = size();

        boundsCheckIncludingSize(idx, size);

        if (size - idx > 1)
        {
            final int elementOffset = elementOffset(framedElementLength, idx);
            final int copyOffset = elementOffset + framedElementLength;
            final int copyLength = (size - idx - 1) * framedElementLength;

            listBuffer.putBytes(elementOffset, listBuffer, copyOffset, copyLength);
        }

        final int lastElementOffset = elementOffset(framedElementLength, size - 1);

        setMemory(lastElementOffset, framedElementLength, 0);
        setSize(size - 1);
    }

    /**
     * Returns the number of contained elements by the list.
     *
     * @return the number of contained elements.
     */
    public int size()
    {
        return listBuffer.getInt(sizeOffset());
    }

    /**
     * Returns the capacity of the list.
     *
     * @return the capacity of the list.
     */
    public int capacity()
    {
        return listBuffer.getInt(capacityOffset());
    }

    /**
     * Returns the maximal element data length.
     *
     * @return the maximal element data length.
     */
    public int maxElementDataLength()
    {
        return listBuffer.getInt(elementMaxLengthOffset());
    }

    /**
     * Get the element from the underlying buffer into a supplied {@link MutableDirectBuffer}.
     *
     * @param idx the element to supply.
     * @param buffer into which the element will be copied.
     * @param offset of the supplied buffer to use.
     */
    public void get(int idx, MutableDirectBuffer buffer, int offset)
    {
        final int size = size();

        boundsCheckIncludingSize(idx, size);

        final int elementOffset = elementOffset(framedElementLength, idx);
        final int length = listBuffer.getInt(elementLengthOffset(elementOffset));

        buffer.putBytes(offset, listBuffer, elementDataOffset(elementOffset), length);
    }

    /**
     * Attach a view of the element to a {@link MutableDirectBuffer} for providing direct access.
     *
     * @param idx the element to attach.
     * @param buffer to which the view of the element is attached.
     */
    public void wrap(int idx, MutableDirectBuffer buffer)
    {
        final int size = size();

        boundsCheckIncludingSize(idx, size);

        final int elementOffset = elementOffset(framedElementLength, idx);
        final int length = listBuffer.getInt(elementLengthOffset(elementOffset));

        buffer.wrap(listBuffer, elementDataOffset(elementOffset), length);
    }

    /**
     * Searches the list for the specified key using the binary
     * search algorithm.  The list must be sorted into ascending order.
     * If it is not sorted, the results are undefined.  If the list
     * contains multiple elements equal to the specified object, there is no
     * guarantee which one will be found.
     *
     * @param key to be searched for.
     * @param c used for comparison.
     *
     * @return the index of the search key, if it is contained in the list;
     *         otherwise, <tt>(-(<i>insertion point</i>) - 1)</tt>.  The
     *         <i>insertion point</i> is defined as the point at which the
     *         key would be inserted into the list: the index of the first
     *         element greater than the key, or <tt>list.size()</tt> if all
     *         elements in the list are less than the specified key.  Note
     *         that this guarantees that the return value will be &gt;= 0 if
     *         and only if the key is found.
     */
    @SuppressWarnings("unchecked")
    public <T extends DirectBuffer> int find(final T key, final Comparator<T> c)
    {
        int low = 0;
        int high = size() - 1;

        while (low <= high)
        {
            final int mid = (low + high) >>> 1;
            wrap(mid, elementBuffer);
            final int cmp = c.compare((T) elementBuffer, key);

            if (cmp < 0)
            {
                low = mid + 1;
            }
            else if (cmp > 0)
            {
                high = mid - 1;
            }
            else
            {
                return mid; // key found
            }
        }

        return -(low + 1);  // key not found
    }

    /**
     * Copy the underlying buffer into a supplied {@link MutableDirectBuffer}.
     *
     * @param buffer into which the underlying buffer is copied.
     * @param offset of the supplied buffer to use.
     */
    public void copyInto(MutableDirectBuffer buffer, int offset)
    {
        buffer.putBytes(offset, listBuffer, 0, listBuffer.capacity());
    }

    public void clear()
    {
        final int size = size();
        final int start = elementOffset(framedElementLength, 0);
        final int end = elementOffset(framedElementLength, size);

        setMemory(start, end, 0);
        setSize(0);
    }

    protected void boundsCheck(final int index, final int size)
    {
        if (index < 0 || index > size)
        {
            throw indexOutOfBoundsException(index, size);
        }
    }

    protected void boundsCheckIncludingSize(final int index, final int size)
    {
        if (index < 0 || index >= size)
        {
            throw indexOutOfBoundsException(index, size);
        }
    }

    protected IndexOutOfBoundsException indexOutOfBoundsException(final int index, final int size)
    {
        final String message = String.format("index=%d, size=%d", index, size);
        return new IndexOutOfBoundsException(message);
    }

    protected void elementLengthCheck(final int length)
    {
        final int maxElementDataLength = maxElementDataLength();
        if (maxElementDataLength < length)
        {
            final String message = String.format("Element length larger than maximum size: %d", maxElementDataLength);
            throw new IllegalArgumentException(message);
        }
    }

    protected void setSize(final int size)
    {
        listBuffer.putInt(sizeOffset(), size);
    }

    protected void setValue(DirectBuffer buffer, int offset, int length, int idx)
    {
        final int elementOffset = elementOffset(framedElementLength, idx);
        setValue(buffer, offset, length, idx, elementOffset);
    }

    protected void setValue(DirectBuffer buffer, int offset, int length, int idx, int elementOffset)
    {
        setMemory(elementOffset, framedElementLength, 0);
        listBuffer.putInt(elementLengthOffset(elementOffset), length);
        listBuffer.putBytes(elementDataOffset(elementOffset), buffer, offset, length);
    }

    protected void setMemory(final int idx, final int length, int value)
    {
        listBuffer.setMemory(idx, length, (byte) value);
    }

    @Override
    public CompactListIterator iterator()
    {
        iterator.reset();

        return iterator;
    }

}
