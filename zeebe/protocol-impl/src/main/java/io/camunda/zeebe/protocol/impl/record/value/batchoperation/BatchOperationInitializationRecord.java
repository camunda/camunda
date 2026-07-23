/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationInitializationRecordValue;

public final class BatchOperationInitializationRecord extends UnifiedRecordValue
    implements BatchOperationInitializationRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_STORAGE_ORDINAL_KEY = "storageOrdinalKey";
  public static final String PROP_SEARCH_RESULT_CURSOR_KEY = "searchResultCursor";
  public static final String PROP_SEARCH_QUERY_PAGE_SIZE = "searchQueryPageSize";

  private final LongProperty batchOperationKeyProp = new LongProperty(PROP_BATCH_OPERATION_KEY);
  private final IntegerProperty storageOrdinalKeyProp =
      new IntegerProperty(PROP_STORAGE_ORDINAL_KEY, 0);

  private final StringProperty searchResultCursorProp =
      new StringProperty(PROP_SEARCH_RESULT_CURSOR_KEY, "");
  private final IntegerProperty searchQueryPageSize =
      new IntegerProperty(PROP_SEARCH_QUERY_PAGE_SIZE, 0);

  public BatchOperationInitializationRecord() {
    super(4);
    declareProperty(batchOperationKeyProp)
        .declareProperty(storageOrdinalKeyProp)
        .declareProperty(searchResultCursorProp)
        .declareProperty(searchQueryPageSize);
  }

  @Override
  public long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public BatchOperationInitializationRecord setBatchOperationKey(final Long batchOperationKey) {
    batchOperationKeyProp.reset();
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }

  @Override
  public int getStorageOrdinalKey() {
    return storageOrdinalKeyProp.getValue();
  }

  public BatchOperationInitializationRecord setStorageOrdinalKey(final int storageOrdinalKey) {
    storageOrdinalKeyProp.setValue(storageOrdinalKey);
    return this;
  }

  @Override
  public String getSearchResultCursor() {
    return bufferAsString(searchResultCursorProp.getValue());
  }

  public BatchOperationInitializationRecord setSearchResultCursor(final String cursor) {
    searchResultCursorProp.reset();
    searchResultCursorProp.setValue(cursor);
    return this;
  }

  @Override
  public int getSearchQueryPageSize() {
    return searchQueryPageSize.getValue();
  }

  public BatchOperationInitializationRecord setSearchQueryPageSize(final int pageSize) {
    searchQueryPageSize.reset();
    searchQueryPageSize.setValue(pageSize);
    return this;
  }

  public void wrap(final BatchOperationInitializationRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setStorageOrdinalKey(record.getStorageOrdinalKey());
    setSearchResultCursor(record.getSearchResultCursor());
    setSearchQueryPageSize(record.getSearchQueryPageSize());
  }
}
