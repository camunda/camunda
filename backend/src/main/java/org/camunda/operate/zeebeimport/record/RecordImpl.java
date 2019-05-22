/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.record;

import java.time.Instant;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordValue;

public class RecordImpl<T extends RecordValue> implements Record<T> {
  private long key;
  private long position;
  private Instant timestamp;
  private int raftTerm;
  private int producerId;
  private long sourceRecordPosition;

  private RecordMetadataImpl metadata;
  private T value;

  public RecordImpl() {
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public Instant getTimestamp() {
    return timestamp;
  }

  @Override
  public int getProducerId() {
    return producerId;
  }

  @Override
  public long getSourceRecordPosition() {
    return sourceRecordPosition;
  }

  @Override
  public RecordMetadataImpl getMetadata() {
    return metadata;
  }

  @Override
  public T getValue() {
    return value;
  }

  public void setKey(long key) {
    this.key = key;
  }

  public void setPosition(long position) {
    this.position = position;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }

  public void setRaftTerm(int raftTerm) {
    this.raftTerm = raftTerm;
  }

  public void setProducerId(int producerId) {
    this.producerId = producerId;
  }

  public void setSourceRecordPosition(long sourceRecordPosition) {
    this.sourceRecordPosition = sourceRecordPosition;
  }

  public void setMetadata(RecordMetadataImpl metadata) {
    this.metadata = metadata;
  }

  public void setValue(T value) {
    this.value = value;
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

  @Override
  public String toString() {
    return "RecordImpl{"
        + "key="
        + key
        + ", position="
        + position
        + ", timestamp="
        + timestamp
        + ", raftTerm="
        + raftTerm
        + ", producerId="
        + producerId
        + ", sourceRecordPosition="
        + sourceRecordPosition
        + ", metadata="
        + metadata
        + ", value="
        + value
        + '}';
  }
}
