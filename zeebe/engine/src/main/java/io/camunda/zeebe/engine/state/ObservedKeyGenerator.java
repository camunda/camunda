/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.function.LongConsumer;

/** Tells the stores every key the engine generates, see {@link ProcessingStateStores}. */
record ObservedKeyGenerator(KeyGenerator delegate, LongConsumer onKeyGenerated)
    implements KeyGenerator {

  @Override
  public int partitionId() {
    return delegate.partitionId();
  }

  @Override
  public long nextKey() {
    final long key = delegate.nextKey();
    onKeyGenerated.accept(key);
    return key;
  }

  @Override
  public long getCurrentKey() {
    return delegate.getCurrentKey();
  }
}
