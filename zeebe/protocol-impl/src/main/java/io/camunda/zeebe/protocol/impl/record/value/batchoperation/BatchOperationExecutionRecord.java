/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import java.util.Set;
import java.util.stream.Collectors;

public final class BatchOperationExecutionRecord extends UnifiedRecordValue
    implements BatchOperationExecutionRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_STORAGE_ORDINAL_KEY = "storageOrdinalKey";
  public static final String PROP_ITEM_KEY_LIST = "itemKeys";

  private final LongProperty batchOperationKeyProp = new LongProperty(PROP_BATCH_OPERATION_KEY);
  private final IntegerProperty storageOrdinalKeyProp =
      new IntegerProperty(PROP_STORAGE_ORDINAL_KEY, 0);
  private final ArrayProperty<LongValue> itemKeysProp =
      new ArrayProperty<>(PROP_ITEM_KEY_LIST, LongValue::new);

  public BatchOperationExecutionRecord() {
    super(3);
    declareProperty(batchOperationKeyProp)
        .declareProperty(storageOrdinalKeyProp)
        .declareProperty(itemKeysProp);
  }

  @Override
  public long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public BatchOperationExecutionRecord setBatchOperationKey(final Long batchOperationKey) {
    batchOperationKeyProp.reset();
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }

  @Override
  public int getStorageOrdinalKey() {
    return storageOrdinalKeyProp.getValue();
  }

  public BatchOperationExecutionRecord setStorageOrdinalKey(final int storageOrdinalKey) {
    storageOrdinalKeyProp.setValue(storageOrdinalKey);
    return this;
  }

  @Override
  public Set<Long> getItemKeys() {
    return itemKeysProp.stream().map(LongValue::getValue).collect(Collectors.toSet());
  }

  public BatchOperationExecutionRecord setItemKeys(final Set<Long> keys) {
    itemKeysProp.reset();
    keys.forEach(key -> itemKeysProp.add().setValue(key));
    return this;
  }

  public BatchOperationExecutionRecord wrap(final BatchOperationExecutionRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setStorageOrdinalKey(record.getStorageOrdinalKey());
    setItemKeys(record.getItemKeys());
    return this;
  }
}
