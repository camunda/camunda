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
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Represents a chunk of itemKeys of a batch operation that is persisted in the database. */
public class PersistedBatchOperationChunk extends UnpackedObject implements DbValue {

  /** The key of the chunk. This key os unique just for the batch operation it belongs to. */
  private final LongProperty keyProp = new LongProperty("key");

  /** The key of the batch operation this chunk belongs to. */
  private final LongProperty batchOperationKeyProp = new LongProperty("batchOperationKey");

  /**
   * The itemKeys of the batch operation chunk. This is an array of itemKeys that are part of the
   * batch operation.
   */
  private final ArrayProperty<LongValue> itemKeysProp =
      new ArrayProperty<>("itemKeys", LongValue::new);

  /**
   * The storage ordinal of the item at the same position in {@link #itemKeysProp}. Chunks persisted
   * before ordinals were tracked have no entries here, their items read as ordinal 0.
   */
  private final ArrayProperty<IntegerValue> itemStorageOrdinalsProp =
      new ArrayProperty<>("itemStorageOrdinals", IntegerValue::new);

  public PersistedBatchOperationChunk() {
    super(4);
    declareProperty(keyProp)
        .declareProperty(batchOperationKeyProp)
        .declareProperty(itemKeysProp)
        .declareProperty(itemStorageOrdinalsProp);
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public PersistedBatchOperationChunk setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }

  public long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public PersistedBatchOperationChunk setBatchOperationKey(final long batchOperationKey) {
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }

  public List<Long> getItemKeys() {
    return itemKeysProp.stream().map(LongValue::getValue).toList();
  }

  /** Replaces the items of the chunk with bare keys, their storage ordinal reads as 0. */
  public PersistedBatchOperationChunk setItemKeys(final Collection<Long> itemKeys) {
    itemKeysProp.reset();
    itemStorageOrdinalsProp.reset();
    itemKeys.forEach(key -> itemKeysProp.add().setValue(key));
    return this;
  }

  /** Returns the items of the chunk with their storage ordinals, in chunk order. */
  public List<ChunkItem> getItems() {
    final var itemKeys = getItemKeys();
    final var ordinals = itemStorageOrdinalsProp.stream().map(IntegerValue::getValue).toList();
    final var items = new ArrayList<ChunkItem>(itemKeys.size());
    for (int i = 0; i < itemKeys.size(); i++) {
      final int ordinal = i < ordinals.size() ? ordinals.get(i) : StorageOrdinalRelated.MAIN_INDEX;
      items.add(new ChunkItem(itemKeys.get(i), ordinal));
    }
    return items;
  }

  /** Replaces the items of the chunk, keys and storage ordinals alike. */
  public PersistedBatchOperationChunk setItems(final Collection<ChunkItem> items) {
    itemKeysProp.reset();
    itemStorageOrdinalsProp.reset();
    for (final var item : items) {
      itemKeysProp.add().setValue(item.itemKey());
      itemStorageOrdinalsProp.add().setValue(item.storageOrdinal());
    }
    return this;
  }

  public PersistedBatchOperationChunk appendItemKey(final Long itemKey) {
    itemKeysProp.add().setValue(itemKey);
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
}
