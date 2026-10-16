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
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.LogObjectValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class LogObject extends ObjectValue implements LogObjectValue {

  private final StringProperty keyProperty = new StringProperty("key", "");
  private final LongProperty termProperty = new LongProperty("term", 0L);
  private final LongProperty firstIndexProperty = new LongProperty("firstIndex", -1L);
  private final LongProperty lastIndexProperty = new LongProperty("lastIndex", -1L);
  private final LongProperty firstPositionProperty = new LongProperty("firstPosition", -1L);
  private final LongProperty lastPositionProperty = new LongProperty("lastPosition", -1L);
  private final LongProperty entryCountProperty = new LongProperty("entryCount", 0L);
  private final LongProperty sizeBytesProperty = new LongProperty("sizeBytes", 0L);
  private final StringProperty sha256Property = new StringProperty("sha256", "");

  public LogObject() {
    super(9);
    declareProperty(keyProperty)
        .declareProperty(termProperty)
        .declareProperty(firstIndexProperty)
        .declareProperty(lastIndexProperty)
        .declareProperty(firstPositionProperty)
        .declareProperty(lastPositionProperty)
        .declareProperty(entryCountProperty)
        .declareProperty(sizeBytesProperty)
        .declareProperty(sha256Property);
  }

  @Override
  public String getKey() {
    return BufferUtil.bufferAsString(keyProperty.getValue());
  }

  @Override
  public long getTerm() {
    return termProperty.getValue();
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
  public long getEntryCount() {
    return entryCountProperty.getValue();
  }

  @Override
  public long getSizeBytes() {
    return sizeBytesProperty.getValue();
  }

  @Override
  public String getSha256() {
    return BufferUtil.bufferAsString(sha256Property.getValue());
  }

  public LogObject setKey(final String key) {
    keyProperty.setValue(key);
    return this;
  }

  public LogObject setTerm(final long term) {
    termProperty.setValue(term);
    return this;
  }

  public LogObject setFirstIndex(final long firstIndex) {
    firstIndexProperty.setValue(firstIndex);
    return this;
  }

  public LogObject setLastIndex(final long lastIndex) {
    lastIndexProperty.setValue(lastIndex);
    return this;
  }

  public LogObject setFirstPosition(final long firstPosition) {
    firstPositionProperty.setValue(firstPosition);
    return this;
  }

  public LogObject setLastPosition(final long lastPosition) {
    lastPositionProperty.setValue(lastPosition);
    return this;
  }

  public LogObject setEntryCount(final long entryCount) {
    entryCountProperty.setValue(entryCount);
    return this;
  }

  public LogObject setSizeBytes(final long sizeBytes) {
    sizeBytesProperty.setValue(sizeBytes);
    return this;
  }

  public LogObject setSha256(final String sha256) {
    sha256Property.setValue(sha256);
    return this;
  }

  public LogObject copy(final LogObjectValue object) {
    setKey(object.getKey());
    setTerm(object.getTerm());
    setFirstIndex(object.getFirstIndex());
    setLastIndex(object.getLastIndex());
    setFirstPosition(object.getFirstPosition());
    setLastPosition(object.getLastPosition());
    setEntryCount(object.getEntryCount());
    setSizeBytes(object.getSizeBytes());
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
