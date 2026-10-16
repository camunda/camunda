/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.management;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.IndexChunkValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class IndexChunk extends ObjectValue implements IndexChunkValue {

  private final LongProperty chunkNoProperty = new LongProperty("chunkNo", -1L);
  private final StringProperty keyProperty = new StringProperty("key", "");
  private final LongProperty firstIndexProperty = new LongProperty("firstIndex", -1L);
  private final LongProperty lastIndexProperty = new LongProperty("lastIndex", -1L);
  private final LongProperty firstPositionProperty = new LongProperty("firstPosition", -1L);
  private final LongProperty lastPositionProperty = new LongProperty("lastPosition", -1L);
  private final LongProperty objectCountProperty = new LongProperty("objectCount", 0L);
  private final LongProperty firstCheckpointIdProperty = new LongProperty("firstCheckpointId", -1L);
  private final LongProperty lastCheckpointIdProperty = new LongProperty("lastCheckpointId", -1L);
  private final StringProperty sha256Property = new StringProperty("sha256", "");

  public IndexChunk() {
    super(10);
    declareProperty(chunkNoProperty)
        .declareProperty(keyProperty)
        .declareProperty(firstIndexProperty)
        .declareProperty(lastIndexProperty)
        .declareProperty(firstPositionProperty)
        .declareProperty(lastPositionProperty)
        .declareProperty(objectCountProperty)
        .declareProperty(firstCheckpointIdProperty)
        .declareProperty(lastCheckpointIdProperty)
        .declareProperty(sha256Property);
  }

  @Override
  public long getChunkNo() {
    return chunkNoProperty.getValue();
  }

  @Override
  public String getKey() {
    return BufferUtil.bufferAsString(keyProperty.getValue());
  }

  @Override
  public long getFirstIndex() {
    return firstIndexProperty.getValue();
  }

  @Override
  public long getLastIndex() {
    return lastIndexProperty.getValue();
  }

  @Override
  public long getFirstPosition() {
    return firstPositionProperty.getValue();
  }

  @Override
  public long getLastPosition() {
    return lastPositionProperty.getValue();
  }

  @Override
  public long getObjectCount() {
    return objectCountProperty.getValue();
  }

  @Override
  public long getFirstCheckpointId() {
    return firstCheckpointIdProperty.getValue();
  }

  @Override
  public long getLastCheckpointId() {
    return lastCheckpointIdProperty.getValue();
  }

  @Override
  public String getSha256() {
    return BufferUtil.bufferAsString(sha256Property.getValue());
  }

  public IndexChunk setChunkNo(final long chunkNo) {
    chunkNoProperty.setValue(chunkNo);
    return this;
  }

  public IndexChunk setKey(final String key) {
    keyProperty.setValue(key);
    return this;
  }

  public IndexChunk setFirstIndex(final long firstIndex) {
    firstIndexProperty.setValue(firstIndex);
    return this;
  }

  public IndexChunk setLastIndex(final long lastIndex) {
    lastIndexProperty.setValue(lastIndex);
    return this;
  }

  public IndexChunk setFirstPosition(final long firstPosition) {
    firstPositionProperty.setValue(firstPosition);
    return this;
  }

  public IndexChunk setLastPosition(final long lastPosition) {
    lastPositionProperty.setValue(lastPosition);
    return this;
  }

  public IndexChunk setObjectCount(final long objectCount) {
    objectCountProperty.setValue(objectCount);
    return this;
  }

  public IndexChunk setFirstCheckpointId(final long firstCheckpointId) {
    firstCheckpointIdProperty.setValue(firstCheckpointId);
    return this;
  }

  public IndexChunk setLastCheckpointId(final long lastCheckpointId) {
    lastCheckpointIdProperty.setValue(lastCheckpointId);
    return this;
  }

  public IndexChunk setSha256(final String sha256) {
    sha256Property.setValue(sha256);
    return this;
  }

  public IndexChunk copy(final IndexChunkValue object) {
    setChunkNo(object.getChunkNo());
    setKey(object.getKey());
    setFirstIndex(object.getFirstIndex());
    setLastIndex(object.getLastIndex());
    setFirstPosition(object.getFirstPosition());
    setLastPosition(object.getLastPosition());
    setObjectCount(object.getObjectCount());
    setFirstCheckpointId(object.getFirstCheckpointId());
    setLastCheckpointId(object.getLastCheckpointId());
    setSha256(object.getSha256());
    return this;
  }

  /** hashCode relies on implementation provided by {@link ObjectValue#hashCode()} */
  @Override
  public int hashCode() {
    return super.hashCode();
  }

  /** equals relies on implementation provided by {@link ObjectValue#equals(Object)} */
  @Override
  public boolean equals(final Object o) {
    return super.equals(o);
  }
}
