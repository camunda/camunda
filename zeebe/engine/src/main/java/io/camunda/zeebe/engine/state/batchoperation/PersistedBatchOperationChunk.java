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
import java.util.List;
import java.util.Set;

public class PersistedBatchOperationChunk extends UnpackedObject implements DbValue {

  private final LongProperty keyProp = new LongProperty("key");
  private final LongProperty batchOperationKeyProp = new LongProperty("batchOperationKey");
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

  public PersistedBatchOperationChunk appendItemKey(final Long itemKey) {
    itemKeysProp.add().setValue(itemKey);
    return this;
  }

  public PersistedBatchOperationChunk removeItemKeys(final Set<Long> itemKeys) {
    final var newKeys =
        itemKeysProp.stream().map(LongValue::getValue).filter(k -> !itemKeys.contains(k)).toList();

    itemKeysProp.reset();

    for (final var key : newKeys) {
      itemKeysProp.add().setValue(key);
    }

    return this;
  }
}
