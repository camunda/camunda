/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.value;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.function.Supplier;
import org.agrona.collections.CollectionUtil;

// avoids allocation, but only efficient with an underlying collection that supports RandomAccess
@SuppressWarnings("ForLoopReplaceableByForEach")
public final class ArrayValue<T extends BaseValue> extends BaseValue
    implements Iterable<T>, RandomAccess {
  private final List<T> items;
  private final Supplier<T> valueFactory;

  public ArrayValue(final Supplier<T> valueFactory) {
    this.valueFactory = valueFactory;

    items = new ArrayList<>();
  }

  @Override
  public void reset() {
    items.clear();
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }

  @Override
  public void writeJSON(final StringBuilder builder) {
    builder.append("[");

    for (int i = 0; i < items.size(); i++) {
      if (i > 0) {
        builder.append(",");
      }

      items.get(i).writeJSON(builder);
    }

    builder.append("]");
  }

  @Override
  public void write(final MsgPackWriter writer) {
    writer.writeArrayHeader(items.size());
    for (int i = 0; i < items.size(); i++) {
      items.get(i).write(writer);
    }
  }

  @Override
  public void read(final MsgPackReader reader) {
    reset();

    final var size = reader.readArrayHeader();
    for (int i = 0; i < size; i++) {
      final var value = valueFactory.get();
      value.read(reader);
      items.add(i, value);
    }
  }

  @Override
  public int getEncodedLength() {
    return MsgPackWriter.getEncodedArrayHeaderLenght(items.size())
        + CollectionUtil.sum(items, BaseValue::getEncodedLength);
  }

  @Override
  public Iterator<T> iterator() {
    return items.iterator();
  }

  @Override
  public int hashCode() {
    return Objects.hash(items);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof final ArrayValue<?> that)) {
      return false;
    }

    return items.equals(that.items);
  }

  public T add() {
    final var item = valueFactory.get();
    items.add(item);

    return item;
  }

  public T add(final int index) {
    final var item = valueFactory.get();
    items.add(index, item);
    return item;
  }

  public T get(final int index) {
    return items.get(index);
  }

  public T remove(final int index) {
    return items.remove(index);
  }

  public int size() {
    return items.size();
  }

  public void remove(final Set<T> values) {
    shiftDown(values);
  }

  /**
   * Removes the specified items from the list by shifting down the remaining elements.
   *
   * @param itemsToRemove the set of items to be removed from the list
   */
  private void shiftDown(final Set<T> itemsToRemove) {
    int current = 0;
    for (int src = 0; src < items.size(); ++src) {
      // If the current item is not in the itemsToRemove set, copy it to the 'current' position
      // Otherwise, we skip the item and DON'T increment current.
      // With this we will drop it out later when we copy the next wanted item to current.
      if (!itemsToRemove.contains(items.get(src))) {
        items.set(current++, items.get(src));
      }
    }
    // Clear the remaining items from the list
    items.subList(current, items.size()).clear();
  }
}
