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
package io.zeebe.util.sched;

import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.Queue;

public class ClosedQueue implements Queue<ActorJob>, Deque<ActorJob> {

  @Override
  public int size() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEmpty() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean contains(Object o) {
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
  public boolean addAll(Collection<? extends ActorJob> c) {
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
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean add(ActorJob e) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean offer(ActorJob e) {
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
  public void addFirst(ActorJob actorJob) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addLast(ActorJob actorJob) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean offerFirst(ActorJob actorJob) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean offerLast(ActorJob actorJob) {
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
  public boolean removeFirstOccurrence(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeLastOccurrence(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void push(ActorJob actorJob) {
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
