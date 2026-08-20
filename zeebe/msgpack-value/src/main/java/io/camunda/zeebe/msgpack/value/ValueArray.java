/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.value;

import java.util.RandomAccess;
import java.util.stream.Stream;

public interface ValueArray<T> extends Iterable<T>, RandomAccess {
  T add();

  T add(final int index);

  /**
   * Returns the element at the given index.
   *
   * @throws IndexOutOfBoundsException if the index is out of range
   */
  T get(final int index);

  /**
   * Removes and returns the element at the given index; subsequent elements shift left.
   *
   * @throws IndexOutOfBoundsException if the index is out of range
   */
  T remove(final int index);

  int size();

  Stream<T> stream();
}
