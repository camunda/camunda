/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.collection;

import java.util.Iterator;

public class IntListIterator implements IntIterator {

  protected final Iterator<Integer> innerIterator;

  public IntListIterator(Iterable<Integer> iterable) {
    innerIterator = iterable.iterator();
  }

  @Override
  public boolean hasNext() {
    return innerIterator.hasNext();
  }

  @Override
  public Integer next() {
    return innerIterator.next();
  }

  @Override
  public int nextInt() {
    final Integer nextValue = next();
    if (nextValue == null) {
      throw new RuntimeException("unexpected null value");
    }

    return nextValue;
  }
}
