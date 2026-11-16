/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.LongArrayList;
import org.jspecify.annotations.NullMarked;

/** Represents a chunk of itemKeys of a batch operation that is persisted in the database. */
@MsgpackPojo
@NullMarked
public final class PersistedBatchOperationChunk
    extends MsgpackBufferValue<PersistedBatchOperationChunk> implements DbValue {

  /** The key of the chunk. This key os unique just for the batch operation it belongs to. */
  final RequiredLong key = new RequiredLong();

  /** The key of the batch operation this chunk belongs to. */
  final RequiredLong batchOperationKey = new RequiredLong();

  /**
   * The itemKeys of the batch operation chunk. This is an array of itemKeys that are part of the
   * batch operation.
   */
  final LongArrayList itemKeys = new LongArrayList();

  /**
   * The storage ordinal of the item at the same position in {@link #itemKeys}. Chunks persisted
   * before ordinals were tracked have no entries here, their items read as ordinal 0.
   */
  final IntArrayList itemStorageOrdinals = new IntArrayList();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public long getKey() {
    return key.get();
  }

  public PersistedBatchOperationChunk setKey(final long key) {
    this.key.set(key);
    return this;
  }

  public long getBatchOperationKey() {
    return batchOperationKey.get();
  }

  public PersistedBatchOperationChunk setBatchOperationKey(final long batchOperationKey) {
    this.batchOperationKey.set(batchOperationKey);
    return this;
  }

  public List<Long> getItemKeys() {
    return itemKeys.stream().toList();
  }

  /** Replaces the items of the chunk with bare keys, their storage ordinal reads as 0. */
  public PersistedBatchOperationChunk setItemKeys(final Collection<Long> itemKeys) {
    this.itemKeys.clear();
    itemStorageOrdinals.clear();
    itemKeys.forEach(key -> this.itemKeys.addLong(key));
    return this;
  }

  /** Returns the items of the chunk with their storage ordinals, in chunk order. */
  public List<ChunkItem> getItems() {
    final var itemKeys = getItemKeys();
    final var ordinals = itemStorageOrdinals.stream().toList();
    final var items = new ArrayList<ChunkItem>(itemKeys.size());
    for (int i = 0; i < itemKeys.size(); i++) {
      final int ordinal = i < ordinals.size() ? ordinals.get(i) : StorageOrdinalRelated.MAIN_INDEX;
      items.add(new ChunkItem(itemKeys.get(i), ordinal));
    }
    return items;
  }

  /** Replaces the items of the chunk, keys and storage ordinals alike. */
  public PersistedBatchOperationChunk setItems(final Collection<ChunkItem> items) {
    itemKeys.clear();
    itemStorageOrdinals.clear();
    for (final var item : items) {
      itemKeys.addLong(item.itemKey());
      itemStorageOrdinals.addInt(item.storageOrdinal());
    }
    return this;
  }

  public PersistedBatchOperationChunk appendItemKey(final Long itemKey) {
    itemKeys.addLong(itemKey);
    return this;
  }

  /** Removes the given itemKeys from the chunk. */
  public PersistedBatchOperationChunk removeItemKeys(final Set<Long> itemKeys) {
    final var remaining =
        getItems().stream().filter(item -> !itemKeys.contains(item.itemKey())).toList();

    // This is needed since ArrayProperty does not support removing items
    return setItems(remaining);
  }

  /** One item of the chunk: its key and the storage ordinal its entity lives under. */
  public record ChunkItem(long itemKey, int storageOrdinal) {}

  @Override
  protected MsgpackCodec<PersistedBatchOperationChunk> codec() {
    return PersistedBatchOperationChunkCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedBatchOperationChunk> ops() {
    return PersistedBatchOperationChunkOps.INSTANCE;
  }
}
