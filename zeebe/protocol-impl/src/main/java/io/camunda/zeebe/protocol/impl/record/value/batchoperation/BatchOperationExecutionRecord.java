/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;

public final class BatchOperationExecutionRecord extends UnifiedRecordValue
    implements BatchOperationExecutionRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_ITEM_KEY = "itemKey";

  private final LongProperty batchOperationKeyProp = new LongProperty(PROP_BATCH_OPERATION_KEY);
  private final LongProperty itemKeyProp = new LongProperty(PROP_ITEM_KEY, -1L);

  public BatchOperationExecutionRecord() {
    super(2);
    declareProperty(batchOperationKeyProp).declareProperty(itemKeyProp);
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
  public Long getItemKey() {
    return itemKeyProp.getValue();
  }

  public BatchOperationExecutionRecord setItemKey(final Long key) {
    itemKeyProp.reset();
    itemKeyProp.setValue(key);
    return this;
  }

  public BatchOperationExecutionRecord wrap(final BatchOperationExecutionRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setItemKey(record.getItemKey());
    return this;
  }
}
