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
package io.zeebe.util.sched.channel;

import io.zeebe.util.sched.ActorCondition;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.function.Consumer;

/** Wraps a {@link Queue} as {@link ConsumableChannel}. */
public class ConcurrentQueueChannel<E> implements Queue<E>, ConsumableChannel {
  private final ActorConditions actorConditions = new ActorConditions();

  private final Queue<E> wrapped;

  public ConcurrentQueueChannel(Queue<E> wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public boolean hasAvailable() {
    return !isEmpty();
  }

  @Override
  public boolean add(E e) {
    if (wrapped.add(e)) {
      actorConditions.signalConsumers();
      return true;
    }
    return false;
  }

  @Override
  public boolean offer(E e) {
    if (wrapped.offer(e)) {
      actorConditions.signalConsumers();
      return true;
    }
    return false;
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
  public boolean contains(Object o) {
    return wrapped.contains(o);
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
  public Iterator<E> iterator() {
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
    return wrapped.remove(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return wrapped.containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
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
  public void forEach(Consumer<? super E> action) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void registerConsumer(ActorCondition onDataAvailable) {
    actorConditions.registerConsumer(onDataAvailable);
  }

  @Override
  public void removeConsumer(ActorCondition onDataAvailable) {
    actorConditions.removeConsumer(onDataAvailable);
  }
}
