/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.job;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;

import java.util.Iterator;
import java.util.PrimitiveIterator.OfInt;
import java.util.stream.IntStream;

public class PartitionIdIterator implements Iterator<Integer> {

  private final OfInt iterator;
  private int currentPartitionId;

  public PartitionIdIterator(int startPartitionId, int partitionsCount) {
    iterator =
        IntStream.range(0, partitionsCount)
            .map(index -> ((index + startPartitionId) % partitionsCount) + START_PARTITION_ID)
            .iterator();
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }

  @Override
  public Integer next() {
    currentPartitionId = iterator.next();
    return currentPartitionId;
  }

  public int getCurrentPartitionId() {
    return currentPartitionId;
  }
}
