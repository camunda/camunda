/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue.BatchOperationExecutionItemValue;

/**
 * One item of a batch operation execution: its key and the storage ordinal its entity lives under.
 */
public record BatchOperationExecutionItem(long itemKey, int storageOrdinal)
    implements BatchOperationExecutionItemValue {

  @Override
  public long getItemKey() {
    return itemKey;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }
}
