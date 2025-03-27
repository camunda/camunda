/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import java.util.List;
import java.util.stream.Collectors;

public final class BatchOperationChunkRecord extends UnifiedRecordValue
    implements BatchOperationChunkRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_CHUNK_KEY = "chunkKey";
  public static final String PROP_ITEM_KEY_LIST = "itemKeys";

  private final LongProperty batchOperationKeyProp = new LongProperty(PROP_BATCH_OPERATION_KEY);
  private final LongProperty chunkKeyProp = new LongProperty(PROP_CHUNK_KEY);
  private final ArrayProperty<LongValue> itemKeysProp =
      new ArrayProperty<>(PROP_ITEM_KEY_LIST, LongValue::new);

  public BatchOperationChunkRecord() {
    super(3);
    declareProperty(batchOperationKeyProp)
        .declareProperty(chunkKeyProp)
        .declareProperty(itemKeysProp);
  }

  @Override
  public Long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public BatchOperationChunkRecord setBatchOperationKey(final Long batchOperationKey) {
    batchOperationKeyProp.reset();
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }

  @Override
  public List<Long> getItemKeys() {
    return itemKeysProp.stream().map(LongValue::getValue).collect(Collectors.toList());
  }

  public BatchOperationChunkRecord setItemKeys(final List<Long> keys) {
    itemKeysProp.reset();
    keys.forEach(key -> itemKeysProp.add().setValue(key));
    return this;
  }

  @Override
  public Long getChunkKey() {
    return chunkKeyProp.getValue();
  }

  public BatchOperationChunkRecord setChunkKey(final Long key) {
    chunkKeyProp.setValue(key);
    return this;
  }

  public void wrap(final BatchOperationChunkRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setItemKeys(record.getItemKeys());
    setChunkKey(record.getChunkKey());
  }
}
