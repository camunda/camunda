/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.LongArrayList;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class BatchOperationExecutionRecord
    extends MsgpackBufferValue<BatchOperationExecutionRecord>
    implements MsgpackRecordValue, BatchOperationExecutionRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_STORAGE_ORDINAL = "storageOrdinal";
  public static final String PROP_ITEM_KEY_LIST = "itemKeys";
  public static final String PROP_ITEM_STORAGE_ORDINAL_LIST = "itemStorageOrdinals";

  final RequiredLong batchOperationKey = new RequiredLong();
  int storageOrdinal = 0;
  final LongArrayList itemKeys = new LongArrayList();

  /**
   * The storage ordinal of the item at the same position in {@link #itemKeys}. Records written
   * before ordinals were tracked have no entries here, their items read as ordinal 0.
   */
  final IntArrayList itemStorageOrdinals = new IntArrayList();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getBatchOperationKey() {
    return batchOperationKey.get();
  }

  public BatchOperationExecutionRecord setBatchOperationKey(final Long batchOperationKey) {
    this.batchOperationKey.set(batchOperationKey);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public BatchOperationExecutionRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  public Set<Long> getItemKeys() {
    return itemKeys.stream().collect(Collectors.toSet());
  }

  /** Replaces the items with bare keys, their storage ordinal reads as 0. */
  public BatchOperationExecutionRecord setItemKeys(final Set<Long> keys) {
    itemKeys.clear();
    itemStorageOrdinals.clear();
    keys.forEach(itemKeys::addLong);
    return this;
  }

  @Override
  public List<BatchOperationExecutionItemValue> getItems() {
    final var items = new ArrayList<BatchOperationExecutionItemValue>(itemKeys.size());
    for (int i = 0; i < itemKeys.size(); i++) {
      final int ordinal =
          i < itemStorageOrdinals.size()
              ? itemStorageOrdinals.getInt(i)
              : StorageOrdinalRelated.MAIN_INDEX;
      items.add(new BatchOperationExecutionItem(itemKeys.getLong(i), ordinal));
    }
    return items;
  }

  /** Replaces the items, keys and storage ordinals alike. */
  public BatchOperationExecutionRecord setItems(
      final Collection<? extends BatchOperationExecutionItemValue> items) {
    itemKeys.clear();
    itemStorageOrdinals.clear();
    for (final var item : items) {
      itemKeys.addLong(item.getItemKey());
      itemStorageOrdinals.addInt(item.getStorageOrdinal());
    }
    return this;
  }

  public BatchOperationExecutionRecord wrap(final BatchOperationExecutionRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setStorageOrdinal(record.getStorageOrdinal());
    setItems(record.getItems());
    return this;
  }

  @Override
  protected MsgpackCodec<BatchOperationExecutionRecord> codec() {
    return BatchOperationExecutionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<BatchOperationExecutionRecord> ops() {
    return BatchOperationExecutionRecordOps.INSTANCE;
  }
}
