/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.collection;

import java.util.NoSuchElementException;
import org.agrona.collections.IntArrayList;

public class IntArrayListIterator implements IntIterator {
  private IntArrayList list = null;
  private int cursor = 0;

  public void wrap(IntArrayList list) {
    this.list = list;
    cursor = 0;
  }

  @Override
  public boolean hasNext() {
    return list.size() - cursor > 0;
  }

  @Override
  public Integer next() {
    return nextInt();
  }

  @Override
  public int nextInt() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    } else {
      return list.getInt(cursor++);
    }
  }
}
