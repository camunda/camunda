/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.Queue;

public final class ClosedQueue implements Queue<ActorJob>, Deque<ActorJob> {

  @Override
  public int size() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEmpty() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean contains(final Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<ActorJob> iterator() {
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
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsAll(final Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addAll(final Collection<? extends ActorJob> c) {
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
  public boolean add(final ActorJob e) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean offer(final ActorJob e) {
    e.failFuture("Actor is closed");

    return true;
  }

  @Override
  public ActorJob remove() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ActorJob poll() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ActorJob element() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ActorJob peek() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addFirst(final ActorJob actorJob) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addLast(final ActorJob actorJob) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean offerFirst(final ActorJob actorJob) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean offerLast(final ActorJob actorJob) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ActorJob removeFirst() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ActorJob removeLast() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ActorJob pollFirst() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ActorJob pollLast() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ActorJob getFirst() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ActorJob getLast() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ActorJob peekFirst() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ActorJob peekLast() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeFirstOccurrence(final Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeLastOccurrence(final Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void push(final ActorJob actorJob) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ActorJob pop() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<ActorJob> descendingIterator() {
    throw new UnsupportedOperationException();
  }
}
