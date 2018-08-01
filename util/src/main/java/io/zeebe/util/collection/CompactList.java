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
package io.zeebe.util.collection;

import static io.zeebe.util.collection.CompactListDescriptor.capacityOffset;
import static io.zeebe.util.collection.CompactListDescriptor.elementDataOffset;
import static io.zeebe.util.collection.CompactListDescriptor.elementLengthOffset;
import static io.zeebe.util.collection.CompactListDescriptor.elementMaxLengthOffset;
import static io.zeebe.util.collection.CompactListDescriptor.elementOffset;
import static io.zeebe.util.collection.CompactListDescriptor.framedLength;
import static io.zeebe.util.collection.CompactListDescriptor.requiredBufferCapacity;
import static io.zeebe.util.collection.CompactListDescriptor.sizeOffset;
import static java.lang.Math.max;

import io.zeebe.util.CloseableSilently;
import io.zeebe.util.allocation.AllocatedBuffer;
import io.zeebe.util.allocation.BufferAllocator;
import java.io.InputStream;
import java.util.Comparator;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;

/** Compact, off-heap list datastructure */
public class CompactList implements Iterable<MutableDirectBuffer>, CloseableSilently {
  protected final AllocatedBuffer allocatedBuffer;
  protected final UnsafeBuffer listBuffer;
  protected final UnsafeBuffer elementBuffer = new UnsafeBuffer(0, 0);

  protected final int framedElementLength;

  protected final CompactListIterator iterator;

  public CompactList(int elementMaxLength, int capacity, BufferAllocator bufferAllocator) {
    this(
        bufferAllocator.allocate(requiredBufferCapacity(framedLength(elementMaxLength), capacity)),
        elementMaxLength,
        capacity);
  }

  public CompactList(AllocatedBuffer allocatedBuffer, int elementMaxLength, int capacity) {
    this.allocatedBuffer = allocatedBuffer;

    framedElementLength = framedLength(elementMaxLength);
    final int requiredBufferCapacity = requiredBufferCapacity(framedElementLength, capacity);
    final int bufferCapacity = allocatedBuffer.capacity();

    if (bufferCapacity < requiredBufferCapacity) {
      final String errorMessage =
          String.format(
              "Not enough capacity in provided buffer. Has %d, required %d",
              bufferCapacity, requiredBufferCapacity);
      throw new IllegalArgumentException(errorMessage);
    }

    listBuffer = new UnsafeBuffer(0, 0);
    listBuffer.wrap(allocatedBuffer.getRawBuffer(), 0, requiredBufferCapacity);

    // write header
    listBuffer.putInt(sizeOffset(), 0);
    listBuffer.putInt(elementMaxLengthOffset(), elementMaxLength);
    listBuffer.putInt(capacityOffset(), capacity);

    iterator = new CompactListIterator(this);
  }

  public CompactList(final UnsafeBuffer listBuffer) {
    this.listBuffer = listBuffer;

    final int elementMaxLength = listBuffer.getInt(elementMaxLengthOffset());
    framedElementLength = framedLength(elementMaxLength);

    iterator = new CompactListIterator(this);
    this.allocatedBuffer = null;
  }

  /**
   * Appends the passed element to the end of the list.
   *
   * @param srcBuffer from which the element bytes will be copied.
   */
  public void add(DirectBuffer srcBuffer) {
    add(srcBuffer, 0, srcBuffer.capacity());
  }

  /**
   * Appends the passed element to the end of the list.
   *
   * @param srcBuffer from which the element bytes will be copied.
   * @param offset at which the element begins.
   * @param length of the element.
   */
  public void add(DirectBuffer srcBuffer, int offset, int length) {
    add(srcBuffer, offset, length, max(size(), 0));
  }

  /**
   * Adds the passed element at the given index. Shifts the element currently at that position (if
   * any) and any subsequent elements to the right
   *
   * @param srcBuffer from which the element bytes will be copied.
   * @param offset at which the element begins.
   * @param length of the element.
   * @param idx at which the specified element is to be inserted.
   */
  public void add(DirectBuffer srcBuffer, int offset, int length, int idx) {
    final int size = size();
    final int capacity = capacity();

    elementLengthCheck(length);
    boundsCheck(idx, size);

    if (capacity == size) {
      final String errorMessage =
          String.format("Cannot add element: list is full. Capacity=%d", capacity);
      throw new IllegalArgumentException(errorMessage);
    }

    final int elementOffset = elementOffset(framedElementLength, idx);

    if (size - idx > 0) {
      final int copyOffset = elementOffset + framedElementLength;
      final int copyLength = (size - idx) * framedElementLength;

      listBuffer.putBytes(copyOffset, listBuffer, elementOffset, copyLength);
    }

    setValue(srcBuffer, offset, length, idx, elementOffset);
    setSize(size + 1);
  }

  /**
   * Replaces the element at the specified position in this list with the specified element.
   *
   * @param idx of the element to replace
   * @param srcBuffer from which the element bytes will be copied.
   */
  public void set(int idx, DirectBuffer srcBuffer) {
    set(idx, srcBuffer, 0, srcBuffer.capacity());
  }

  /**
   * Replaces the element at the specified position in this list with the specified element.
   *
   * @param idx of the element to replace
   * @param srcBuffer from which the element bytes will be copied.
   * @param offset at which the element begins.
   * @param length of the element.
   */
  public void set(int idx, DirectBuffer srcBuffer, int offset, int length) {
    final int size = size();

    elementLengthCheck(length);
    boundsCheckIncludingSize(idx, size);

    setValue(srcBuffer, offset, length, idx);
  }

  /**
   * Removes the element at the specified position in this list. Shifts any subsequent elements to
   * the left (subtracts one from their indices).
   *
   * @param idx of the element to be removed.
   */
  public void remove(int idx) {
    final int size = size();

    boundsCheckIncludingSize(idx, size);

    if (size - idx > 1) {
      final int elementOffset = elementOffset(framedElementLength, idx);
      final int copyOffset = elementOffset + framedElementLength;
      final int copyLength = (size - idx - 1) * framedElementLength;

      listBuffer.putBytes(elementOffset, listBuffer, copyOffset, copyLength);
    }

    final int lastElementOffset = elementOffset(framedElementLength, size - 1);

    setMemory(lastElementOffset, framedElementLength, (byte) 0);
    setSize(size - 1);
  }

  /**
   * Returns the number of contained elements by the list.
   *
   * @return the number of contained elements.
   */
  public int size() {
    return listBuffer.getInt(sizeOffset());
  }

  public int sizeVolatile() {
    return listBuffer.getIntVolatile(sizeOffset());
  }

  /**
   * Returns this list's capacity.
   *
   * @return the capacity of the list.
   */
  public int capacity() {
    return listBuffer.getInt(capacityOffset());
  }

  /**
   * Returns the maximal element data length.
   *
   * @return the maximal element data length.
   */
  public int maxElementDataLength() {
    return listBuffer.getInt(elementMaxLengthOffset());
  }

  /**
   * Get the element from the list into a supplied {@link MutableDirectBuffer}.
   *
   * @param idx the element to supply.
   * @param dstBuffer into which the element will be copied.
   * @param offset of the supplied buffer to use.
   * @return the length of the supplied element.
   */
  public int get(int idx, MutableDirectBuffer dstBuffer, int offset) {
    final int size = size();

    boundsCheckIncludingSize(idx, size);

    final int elementOffset = elementOffset(framedElementLength, idx);
    final int length = listBuffer.getInt(elementLengthOffset(elementOffset));

    dstBuffer.putBytes(offset, listBuffer, elementDataOffset(elementOffset), length);

    return length;
  }

  /**
   * Attach a view of the element to a {@link MutableDirectBuffer} for providing direct access.
   *
   * @param idx the element to attach.
   * @param dstBuffer to which the view of the element is attached.
   * @return the length of the attached element.
   */
  public int wrap(int idx, MutableDirectBuffer dstBuffer) {
    final int size = size();

    boundsCheckIncludingSize(idx, size);

    final int elementOffset = elementOffset(framedElementLength, idx);
    final int length = listBuffer.getInt(elementLengthOffset(elementOffset));

    dstBuffer.wrap(listBuffer, elementDataOffset(elementOffset), length);

    return length;
  }

  public int getEncodedSize() {
    return listBuffer.capacity();
  }

  public DirectBuffer getRawBuffer() {
    return listBuffer;
  }

  /**
   * Searches the list for the specified key using the binary search algorithm. The list must be
   * sorted into ascending order. If it is not sorted, the results are undefined. If the list
   * contains multiple elements equal to the specified object, there is no guarantee which one will
   * be found.
   *
   * @param key to be searched for.
   * @param c used for comparison.
   * @return the index of the search key, if it is contained in the list; otherwise,
   *     <tt>(-(<i>insertion point</i>) - 1)</tt>. The <i>insertion point</i> is defined as the
   *     point at which the key would be inserted into the list: the index of the first element
   *     greater than the key, or <tt>size()</tt> if all elements in the list are less than the
   *     specified key. Note that this guarantees that the return value will be &gt;= 0 if and only
   *     if the key is found.
   */
  public int find(final DirectBuffer key, final Comparator<DirectBuffer> c) {
    int low = 0;
    int high = size() - 1;

    while (low <= high) {
      final int mid = (low + high) >>> 1;
      wrap(mid, elementBuffer);
      final int cmp = c.compare(elementBuffer, key);

      if (cmp < 0) {
        low = mid + 1;
      } else if (cmp > 0) {
        high = mid - 1;
      } else {
        return mid; // key found
      }
    }

    return -(low + 1); // key not found
  }

  /**
   * Copy the underlying buffer into a supplied {@link MutableDirectBuffer}.
   *
   * @param dstBuffer into which the underlying buffer is copied.
   * @param offset of the supplied buffer to use.
   * @return the length of the supplied copy of the underlying buffer.
   */
  public int copyInto(MutableDirectBuffer dstBuffer, int offset) {
    final int length = listBuffer.capacity();
    dstBuffer.putBytes(offset, listBuffer, 0, length);

    return length;
  }

  /** Removes all of the elements from this list. The list will be empty after this call returns. */
  public void clear() {
    final int size = size();
    final int start = elementOffset(framedElementLength, 0);
    final int end = framedElementLength * size;

    setMemory(start, end, (byte) 0);
    setSize(0);
  }

  protected void boundsCheck(final int index, final int size) {
    if (index < 0 || index > size) {
      throw indexOutOfBoundsException(index, size);
    }
  }

  protected void boundsCheckIncludingSize(final int index, final int size) {
    if (index < 0 || index >= size) {
      throw indexOutOfBoundsException(index, size);
    }
  }

  protected IndexOutOfBoundsException indexOutOfBoundsException(final int index, final int size) {
    final String message = String.format("map=%d, size=%d", index, size);
    return new IndexOutOfBoundsException(message);
  }

  protected void elementLengthCheck(final int length) {
    final int maxElementDataLength = maxElementDataLength();
    if (maxElementDataLength < length) {
      final String message =
          String.format(
              "Element length larger than maximum size: %d > %d", length, maxElementDataLength);
      throw new IllegalArgumentException(message);
    }
  }

  protected void setSize(final int size) {
    listBuffer.putInt(sizeOffset(), size);
  }

  protected void setValue(DirectBuffer buffer, int offset, int length, int idx) {
    final int elementOffset = elementOffset(framedElementLength, idx);
    setValue(buffer, offset, length, idx, elementOffset);
  }

  protected void setValue(DirectBuffer buffer, int offset, int length, int idx, int elementOffset) {
    setMemory(elementOffset, framedElementLength, (byte) 0);
    listBuffer.putInt(elementLengthOffset(elementOffset), length);
    listBuffer.putBytes(elementDataOffset(elementOffset), buffer, offset, length);
  }

  protected void setMemory(final int idx, final int length, byte value) {
    listBuffer.setMemory(idx, length, value);
  }

  @Override
  public CompactListIterator iterator() {
    iterator.reset();

    return iterator;
  }

  public InputStream toInputStream() {
    return new DirectBufferInputStream(listBuffer);
  }

  @Override
  public void close() {
    if (allocatedBuffer != null) {
      allocatedBuffer.close();
    }
  }
}
