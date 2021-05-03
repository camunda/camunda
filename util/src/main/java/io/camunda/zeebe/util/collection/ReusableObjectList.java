/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.collection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/** An expendable list of reusable objects. */
public final class ReusableObjectList<T extends Reusable> implements Iterable<T> {
  private final ObjectIterator iterator = new ObjectIterator();

  private final List<ReusableElement> elements;
  private final Supplier<T> elementFactory;

  private int size = 0;

  public ReusableObjectList(final Supplier<T> elementFactory) {
    elements = new ArrayList<>();
    this.elementFactory = elementFactory;
  }

  public T add() {
    for (int i = 0; i < elements.size(); i++) {
      final ReusableElement element = elements.get(i);
      if (!element.isSet()) {
        element.set(true);

        size += 1;

        return element.getElement();
      }
    }

    // expend list
    final T newElement = elementFactory.get();

    elements.add(new ReusableElement(newElement));

    size += 1;

    return newElement;
  }

  public void remove(final T element) {
    for (int i = 0; i < elements.size(); i++) {
      final ReusableElement e = elements.get(i);
      if (e.getElement() == element) {
        e.getElement().reset();
        e.set(false);

        size -= 1;
      }
    }
  }

  public T poll() {
    for (int i = 0; i < elements.size(); i++) {
      final ReusableElement element = elements.get(i);

      if (element.isSet()) {
        element.set(false);
        size -= 1;

        return element.getElement();
      }
    }
    return null;
  }

  public int size() {
    return size;
  }

  public void clear() {
    for (final ReusableElement element : elements) {
      element.getElement().reset();
      element.set(false);
    }

    size = 0;
  }

  @Override
  public Iterator<T> iterator() {
    iterator.reset();

    return iterator;
  }

  private class ObjectIterator implements Iterator<T> {
    private ReusableElement current = null;

    private int index = 0;

    public void reset() {
      index = 0;
      current = null;
    }

    @Override
    public boolean hasNext() {
      for (int i = index; i < elements.size(); i++) {
        final ReusableElement element = elements.get(i);

        if (element.isSet()) {
          index = i;
          return true;
        }
      }

      return false;
    }

    @Override
    public T next() {
      if (hasNext()) {
        current = elements.get(index);

        index += 1;

        return current.getElement();
      } else {
        throw new NoSuchElementException();
      }
    }

    @Override
    public void remove() {
      if (current == null) {
        throw new IllegalStateException();
      }

      current.getElement().reset();
      current.set(false);

      size -= 1;
    }
  }

  private class ReusableElement {
    private final T element;

    private boolean isSet = true;

    ReusableElement(final T element) {
      this.element = element;
    }

    public boolean isSet() {
      return isSet;
    }

    public void set(final boolean isSet) {
      this.isSet = isSet;
    }

    public T getElement() {
      return element;
    }
  }
}
