/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import static org.agrona.BitUtil.findNextPositivePowerOfTwo;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

/** Non concurrent, garbage-free array queue with fixed capacity. */
public class BoundedArrayQueue<P> implements Iterable<P>, Queue<P> {
  protected Object[] array;

  protected int capacity;
  protected int mask;
  protected long head;
  protected long tail;

  protected BoundedArrayQueueIterator<P> iterator = new BoundedArrayQueueIterator<>();

  public BoundedArrayQueue(int capacity) {
    this.capacity = findNextPositivePowerOfTwo(capacity);
    this.mask = this.capacity - 1;

    head = 0;
    tail = 0;

    array = new Object[this.capacity];
  }

  @Override
  public int size() {
    return (int) (tail - head);
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public boolean contains(Object o) {
    boolean contains = false;

    for (int i = 0; i < array.length; i++) {
      if (array[i] == o) {
        contains = true;
        break;
      }
    }

    return contains;
  }

  @Override
  public Object[] toArray() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addAll(Collection<? extends P> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    head = 0;
    tail = 0;
    for (int i = 0; i < array.length; i++) {
      array[i] = null;
    }
    iterator.reset();
  }

  public int getCapacity() {
    return capacity;
  }

  @Override
  public Iterator<P> iterator() {
    iterator.open();
    return iterator;
  }

  @Override
  public boolean add(P e) {
    return offer(e);
  }

  @Override
  public boolean offer(P object) {
    final int remainingSpace = capacity - size();

    if (remainingSpace > 0) {
      final int index = (int) (tail & mask);

      array[index] = object;

      ++tail;

      return true;
    } else {
      return false;
    }
  }

  @Override
  public P remove() {
    final P poll = poll();

    if (poll == null) {
      throw new NoSuchElementException();
    }

    return poll;
  }

  @Override
  @SuppressWarnings("unchecked")
  public P poll() {
    final int size = size();

    Object object = null;

    if (size > 0) {
      final int index = (int) (head & mask);

      object = array[index];
      array[index] = null;

      ++head;
    }

    return (P) object;
  }

  @Override
  public P element() {
    final P peek = peek();

    if (peek == null) {
      throw new NoSuchElementException();
    }

    return peek;
  }

  @Override
  @SuppressWarnings("unchecked")
  public P peek() {
    final int size = size();

    Object object = null;

    if (size > 0) {
      final int index = (int) (head & mask);

      object = array[index];
    }

    return (P) object;
  }

  class BoundedArrayQueueIterator<U> implements Iterator<U> {
    protected long iteratorPosition;

    public void reset() {
      iteratorPosition = 0;
    }

    public void open() {
      iteratorPosition = head;
    }

    @Override
    public boolean hasNext() {
      return iteratorPosition < tail;
    }

    @Override
    @SuppressWarnings("unchecked")
    public U next() {
      if (iteratorPosition == tail) {
        throw new NoSuchElementException();
      }

      final Object object = array[(int) iteratorPosition & mask];

      ++iteratorPosition;

      return (U) object;
    }

    @Override
    public void remove() {
      if (iteratorPosition == tail) {
        throw new NoSuchElementException();
      }

      array[(int) iteratorPosition & mask] = null;

      ++head;
    }
  }
}
