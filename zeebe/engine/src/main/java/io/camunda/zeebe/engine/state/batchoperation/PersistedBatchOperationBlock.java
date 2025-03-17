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

public class PersistedBatchOperationBlock extends UnpackedObject implements DbValue {

  private final LongProperty keyProp = new LongProperty("key");
  private final LongProperty batchOperationKeyProp = new LongProperty("batchOperationKey");
  private final ArrayProperty<LongValue> keysProp = new ArrayProperty<>("keys", LongValue::new);

  public PersistedBatchOperationBlock() {
    super(3);
    declareProperty(keyProp).declareProperty(batchOperationKeyProp).declareProperty(keysProp);
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public PersistedBatchOperationBlock setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }

  public long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public PersistedBatchOperationBlock setBatchOperationKey(final long batchOperationKey) {
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }

  public List<Long> getKeys() {
    return keysProp.stream().map(LongValue::getValue).toList();
  }

  public PersistedBatchOperationBlock appendKey(final Long key) {
    keysProp.add().setValue(key);
    return this;
  }

  public PersistedBatchOperationBlock removeKeys(final Collection<Long> keys) {
    final var newKeys =
        keysProp.stream().map(LongValue::getValue).filter(k -> !keys.contains(k)).toList();

    keysProp.reset();

    for (final var key : newKeys) {
      keysProp.add().setValue(key);
    }

    return this;
  }

  public enum BatchOperationState {
    CREATED,
    ACTIVATED,
    PAUSED,
    CANCELED,
    COMPLETED
  }
}
