/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched.channel;

import io.zeebe.util.sched.ActorCondition;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.function.Consumer;

/** Wraps a {@link Queue} as {@link ConsumableChannel}. */
public final class ConcurrentQueueChannel<E> implements Queue<E>, ConsumableChannel {
  private final ActorConditions actorConditions = new ActorConditions();

  private final Queue<E> wrapped;

  public ConcurrentQueueChannel(final Queue<E> wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public boolean hasAvailable() {
    return !isEmpty();
  }

  @Override
  public void registerConsumer(final ActorCondition onDataAvailable) {
    actorConditions.registerConsumer(onDataAvailable);
  }

  @Override
  public void removeConsumer(final ActorCondition onDataAvailable) {
    actorConditions.removeConsumer(onDataAvailable);
  }

  @Override
  public boolean add(final E e) {
    if (wrapped.add(e)) {
      actorConditions.signalConsumers();
      return true;
    }
    return false;
  }

  @Override
  public boolean offer(final E e) {
    if (wrapped.offer(e)) {
      actorConditions.signalConsumers();
      return true;
    }
    return false;
  }

  @Override
  public E remove() {
    return wrapped.remove();
  }

  @Override
  public E poll() {
    return wrapped.poll();
  }

  @Override
  public E element() {
    return wrapped.element();
  }

  @Override
  public E peek() {
    return wrapped.peek();
  }

  @Override
  public int size() {
    return wrapped.size();
  }

  @Override
  public boolean isEmpty() {
    return wrapped.isEmpty();
  }

  @Override
  public boolean contains(final Object o) {
    return wrapped.contains(o);
  }

  @Override
  public Iterator<E> iterator() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object[] toArray() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T[] toArray(final T[] a) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(final Object o) {
    return wrapped.remove(o);
  }

  @Override
  public boolean containsAll(final Collection<?> c) {
    return wrapped.containsAll(c);
  }

  @Override
  public boolean addAll(final Collection<? extends E> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(final Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(final Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void forEach(final Consumer<? super E> action) {
    throw new UnsupportedOperationException();
  }
}
