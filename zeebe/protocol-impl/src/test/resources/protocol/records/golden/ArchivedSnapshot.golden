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
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.ArchivedSnapshotValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class ArchivedSnapshot extends ObjectValue implements ArchivedSnapshotValue {

  private final StringProperty idProperty = new StringProperty("id", "");
  private final LongProperty indexProperty = new LongProperty("index", -1L);
  private final LongProperty termProperty = new LongProperty("term", 0L);
  private final LongProperty processedPositionProperty = new LongProperty("processedPosition", -1L);
  private final LongProperty exportedPositionProperty = new LongProperty("exportedPosition", -1L);
  private final LongProperty lastFollowupEventPositionProperty =
      new LongProperty("lastFollowupEventPosition", -1L);
  private final LongProperty maxExportedPositionProperty =
      new LongProperty("maxExportedPosition", -1L);
  private final LongProperty fileCountProperty = new LongProperty("fileCount", 0L);
  private final LongProperty sizeBytesProperty = new LongProperty("sizeBytes", 0L);

  public ArchivedSnapshot() {
    super(9);
    declareProperty(idProperty)
        .declareProperty(indexProperty)
        .declareProperty(termProperty)
        .declareProperty(processedPositionProperty)
        .declareProperty(exportedPositionProperty)
        .declareProperty(lastFollowupEventPositionProperty)
        .declareProperty(maxExportedPositionProperty)
        .declareProperty(fileCountProperty)
        .declareProperty(sizeBytesProperty);
  }

  @Override
  public String getId() {
    return BufferUtil.bufferAsString(idProperty.getValue());
  }

  @Override
  public long getIndex() {
    return indexProperty.getValue();
  }

  @Override
  public long getTerm() {
    return termProperty.getValue();
  }

  @Override
  public long getProcessedPosition() {
    return processedPositionProperty.getValue();
  }

  @Override
  public long getExportedPosition() {
    return exportedPositionProperty.getValue();
  }

  @Override
  public long getLastFollowupEventPosition() {
    return lastFollowupEventPositionProperty.getValue();
  }

  @Override
  public long getMaxExportedPosition() {
    return maxExportedPositionProperty.getValue();
  }

  @Override
  public long getFileCount() {
    return fileCountProperty.getValue();
  }

  @Override
  public long getSizeBytes() {
    return sizeBytesProperty.getValue();
  }

  public ArchivedSnapshot setId(final String id) {
    idProperty.setValue(id);
    return this;
  }

  public ArchivedSnapshot setIndex(final long index) {
    indexProperty.setValue(index);
    return this;
  }

  public ArchivedSnapshot setTerm(final long term) {
    termProperty.setValue(term);
    return this;
  }

  public ArchivedSnapshot setProcessedPosition(final long processedPosition) {
    processedPositionProperty.setValue(processedPosition);
    return this;
  }

  public ArchivedSnapshot setExportedPosition(final long exportedPosition) {
    exportedPositionProperty.setValue(exportedPosition);
    return this;
  }

  public ArchivedSnapshot setLastFollowupEventPosition(final long lastFollowupEventPosition) {
    lastFollowupEventPositionProperty.setValue(lastFollowupEventPosition);
    return this;
  }

  public ArchivedSnapshot setMaxExportedPosition(final long maxExportedPosition) {
    maxExportedPositionProperty.setValue(maxExportedPosition);
    return this;
  }

  public ArchivedSnapshot setFileCount(final long fileCount) {
    fileCountProperty.setValue(fileCount);
    return this;
  }

  public ArchivedSnapshot setSizeBytes(final long sizeBytes) {
    sizeBytesProperty.setValue(sizeBytes);
    return this;
  }

  public ArchivedSnapshot copy(final ArchivedSnapshotValue object) {
    setId(object.getId());
    setIndex(object.getIndex());
    setTerm(object.getTerm());
    setProcessedPosition(object.getProcessedPosition());
    setExportedPosition(object.getExportedPosition());
    setLastFollowupEventPosition(object.getLastFollowupEventPosition());
    setMaxExportedPosition(object.getMaxExportedPosition());
    setFileCount(object.getFileCount());
    setSizeBytes(object.getSizeBytes());
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
