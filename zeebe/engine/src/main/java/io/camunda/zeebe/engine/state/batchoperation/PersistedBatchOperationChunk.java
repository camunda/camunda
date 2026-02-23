/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Represents a chunk of itemKeys of a batch operation that is persisted in the database. */
public class PersistedBatchOperationChunk extends UnpackedObject implements DbValue {

  /** The key of the chunk. This key os unique just for the batch operation it belongs to. */
  private final LongProperty keyProp = new LongProperty("key");

  /** The key of the batch operation this chunk belongs to. */
  private final LongProperty batchOperationKeyProp = new LongProperty("batchOperationKey");

  /**
   * The itemKeys of the batch operation chunk. This is an array of itemKeys that are part of the
   * batch operation.
   */
  private final ArrayProperty<LongValue> itemKeysProp =
      new ArrayProperty<>("itemKeys", LongValue::new);

  public PersistedBatchOperationChunk() {
    super(3);
    declareProperty(keyProp).declareProperty(batchOperationKeyProp).declareProperty(itemKeysProp);
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public PersistedBatchOperationChunk setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }

  public long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public PersistedBatchOperationChunk setBatchOperationKey(final long batchOperationKey) {
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }

  public List<Long> getItemKeys() {
    return itemKeysProp.stream().map(LongValue::getValue).toList();
  }

  public PersistedBatchOperationChunk setItemKeys(final Collection<Long> itemKeys) {
    itemKeysProp.reset();
    itemKeys.forEach(key -> itemKeysProp.add().setValue(key));
    return this;
  }

  public PersistedBatchOperationChunk appendItemKey(final Long itemKey) {
    itemKeysProp.add().setValue(itemKey);
    return this;
  }

  /** Removes the given itemKeys from the chunk. */
  public PersistedBatchOperationChunk removeItemKeys(final Set<Long> itemKeys) {
    final var newKeys =
        itemKeysProp.stream().map(LongValue::getValue).filter(k -> !itemKeys.contains(k)).toList();

    // This is needed since ArrayProperty does not support removing items
    itemKeysProp.reset();
    for (final var key : newKeys) {
      itemKeysProp.add().setValue(key);
    }

    return this;
  }
}
