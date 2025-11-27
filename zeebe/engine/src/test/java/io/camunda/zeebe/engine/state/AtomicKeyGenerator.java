/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.concurrent.atomic.AtomicLong;

public class AtomicKeyGenerator implements KeyGenerator {

  private final AtomicLong currentKey = new AtomicLong();

  public AtomicKeyGenerator(final int partitionId) {
    currentKey.set(Protocol.encodePartitionId(partitionId, 0));
  }

  @Override
  public long nextKey() {
    return currentKey.incrementAndGet();
  }

  @Override
  public long getCurrentKey() {
    return currentKey.get();
  }
}
