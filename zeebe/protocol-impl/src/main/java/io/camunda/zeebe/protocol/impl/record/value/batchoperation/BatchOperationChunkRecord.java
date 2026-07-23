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
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public final class BatchOperationChunkRecord extends UnifiedRecordValue
    implements BatchOperationChunkRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_STORAGE_ORDINAL_KEY = "storageOrdinalKey";
  public static final String PROP_ITEMS_LIST = "items";

  private final LongProperty batchOperationKeyProp = new LongProperty(PROP_BATCH_OPERATION_KEY);
  private final IntegerProperty storageOrdinalKeyProp =
      new IntegerProperty(PROP_STORAGE_ORDINAL_KEY, 0);
  private final ArrayProperty<BatchOperationItem> itemsProp =
      new ArrayProperty<>(PROP_ITEMS_LIST, BatchOperationItem::new);

  public BatchOperationChunkRecord() {
    super(3);
    declareProperty(batchOperationKeyProp)
        .declareProperty(storageOrdinalKeyProp)
        .declareProperty(itemsProp);
  }

  @Override
  public long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public BatchOperationChunkRecord setBatchOperationKey(final Long batchOperationKey) {
    batchOperationKeyProp.reset();
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }

  @Override
  public int getStorageOrdinalKey() {
    return storageOrdinalKeyProp.getValue();
  }

  public BatchOperationChunkRecord setStorageOrdinalKey(final int storageOrdinalKey) {
    storageOrdinalKeyProp.setValue(storageOrdinalKey);
    return this;
  }

  @Override
  public List<BatchOperationItemValue> getItems() {
    return itemsProp.stream().collect(Collectors.toList());
  }

  public BatchOperationChunkRecord setItems(final Collection<BatchOperationItemValue> items) {
    itemsProp.reset();
    items.forEach(item -> itemsProp.add().wrap(item));
    return this;
  }

  public void wrap(final BatchOperationChunkRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setStorageOrdinalKey(record.getStorageOrdinalKey());
    setItems(record.getItems());
  }
}
