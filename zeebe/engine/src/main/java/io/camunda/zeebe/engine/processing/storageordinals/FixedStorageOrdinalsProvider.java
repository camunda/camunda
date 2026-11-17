/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import java.util.List;

/** Used to support a fixed set of ordinals */
public final class FixedStorageOrdinalsProvider implements StorageOrdinalProvider {
  private final List<Integer> storageOrdinals;
  // this is not threadsafe, but this should only get called on the actor thread
  private int next = 0;

  public FixedStorageOrdinalsProvider(final List<Integer> storageOrdinals) {
    if (storageOrdinals.isEmpty()) {
      throw new IllegalArgumentException("No storage ordinals specified");
    }
    this.storageOrdinals = storageOrdinals;
  }

  @Override
  public int getStorageOrdinal() {
    // use floor mod just in case of negative wrap around
    final var index = Math.floorMod(next, storageOrdinals.size());
    next++;
    return storageOrdinals.get(index);
  }
}
