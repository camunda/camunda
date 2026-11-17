/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationInitializationRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class BatchOperationInitializationRecord
    extends MsgpackBufferValue<BatchOperationInitializationRecord>
    implements MsgpackRecordValue, BatchOperationInitializationRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_STORAGE_ORDINAL = "storageOrdinal";
  public static final String PROP_SEARCH_RESULT_CURSOR_KEY = "searchResultCursor";
  public static final String PROP_SEARCH_QUERY_PAGE_SIZE = "searchQueryPageSize";

  final RequiredLong batchOperationKey = new RequiredLong();
  int storageOrdinal = 0;
  DirectBuffer searchResultCursor = Decoding.EMPTY;
  int searchQueryPageSize = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getBatchOperationKey() {
    return batchOperationKey.get();
  }

  public BatchOperationInitializationRecord setBatchOperationKey(final Long batchOperationKey) {
    this.batchOperationKey.set(batchOperationKey);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public BatchOperationInitializationRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  public String getSearchResultCursor() {
    return bufferAsString(searchResultCursor);
  }

  public BatchOperationInitializationRecord setSearchResultCursor(final String cursor) {
    this.searchResultCursor = BufferUtil.wrapString(cursor);
    return this;
  }

  @Override
  public int getSearchQueryPageSize() {
    return searchQueryPageSize;
  }

  public BatchOperationInitializationRecord setSearchQueryPageSize(final int pageSize) {
    this.searchQueryPageSize = pageSize;
    return this;
  }

  public void wrap(final BatchOperationInitializationRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setStorageOrdinal(record.getStorageOrdinal());
    setSearchResultCursor(record.getSearchResultCursor());
    setSearchQueryPageSize(record.getSearchQueryPageSize());
  }

  @Override
  protected MsgpackCodec<BatchOperationInitializationRecord> codec() {
    return BatchOperationInitializationRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<BatchOperationInitializationRecord> ops() {
    return BatchOperationInitializationRecordOps.INSTANCE;
  }
}
