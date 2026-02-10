/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.util.ArrayDeque;
import java.util.Iterator;

public class BoundedQueue<E> implements Iterable<E> {
  private final int capacity;
  private final ArrayDeque<E> deque;

  public BoundedQueue(final int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("Capacity must be > 0");
    }
    this.capacity = capacity;
    deque = new ArrayDeque<>(capacity);
  }

  public void add(final E element) {
    if (deque.size() == capacity) {
      deque.removeFirst(); // remove oldest
    }
    deque.addLast(element); // add newest
  }

  public int size() {
    return deque.size();
  }

  public E first() {
    return deque.peekFirst();
  }

  @Override
  public Iterator<E> iterator() {
    return deque.iterator();
  }
}
