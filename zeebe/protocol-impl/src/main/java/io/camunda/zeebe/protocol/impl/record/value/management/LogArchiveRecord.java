/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.management;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

public final class LogArchiveRecord extends UnifiedRecordValue implements LogArchiveRecordValue {

  private final StringProperty timelineIdProperty = new StringProperty("timelineId", "");
  private final LongProperty seqProperty = new LongProperty("seq", -1L);
  private final LongProperty startIndexProperty = new LongProperty("startIndex", -1L);
  private final StringProperty parentTimelineIdProperty =
      new StringProperty("parentTimelineId", "");
  private final ArrayProperty<LogObject> objectsProperty =
      new ArrayProperty<>("objects", LogObject::new);
  private final ObjectProperty<ArchivedSnapshot> snapshotProperty =
      new ObjectProperty<>("snapshot", new ArchivedSnapshot());
  private final ObjectProperty<IndexChunk> chunkProperty =
      new ObjectProperty<>("chunk", new IndexChunk());
  private final LongProperty publishedSeqProperty = new LongProperty("publishedSeq", -1L);
  private final LongProperty archivedIndexProperty = new LongProperty("archivedIndex", -1L);
  private final LongProperty archivedPositionProperty = new LongProperty("archivedPosition", -1L);
  private final ArrayProperty<CoveredCheckpoint> checkpointsProperty =
      new ArrayProperty<>("checkpoints", CoveredCheckpoint::new);

  public LogArchiveRecord() {
    super(11);
    declareProperty(timelineIdProperty)
        .declareProperty(seqProperty)
        .declareProperty(startIndexProperty)
        .declareProperty(parentTimelineIdProperty)
        .declareProperty(objectsProperty)
        .declareProperty(snapshotProperty)
        .declareProperty(chunkProperty)
        .declareProperty(publishedSeqProperty)
        .declareProperty(archivedIndexProperty)
        .declareProperty(archivedPositionProperty)
        .declareProperty(checkpointsProperty);
  }

  @Override
  public String getTimelineId() {
    return BufferUtil.bufferAsString(timelineIdProperty.getValue());
  }

  public LogArchiveRecord setTimelineId(final String timelineId) {
    timelineIdProperty.setValue(timelineId);
    return this;
  }

  @Override
  public long getSeq() {
    return seqProperty.getValue();
  }

  public LogArchiveRecord setSeq(final long seq) {
    seqProperty.setValue(seq);
    return this;
  }

  @Override
  public long getStartIndex() {
    return startIndexProperty.getValue();
  }

  public LogArchiveRecord setStartIndex(final long startIndex) {
    startIndexProperty.setValue(startIndex);
    return this;
  }

  @Override
  public String getParentTimelineId() {
    return BufferUtil.bufferAsString(parentTimelineIdProperty.getValue());
  }

  public LogArchiveRecord setParentTimelineId(final String parentTimelineId) {
    parentTimelineIdProperty.setValue(parentTimelineId);
    return this;
  }

  /**
   * Copies each element, because the elements of the underlying array are reused while iterating.
   */
  @Override
  public List<LogObjectValue> getObjects() {
    return objectsProperty.stream()
        .map(element -> (LogObjectValue) new LogObject().copy(element))
        .toList();
  }

  public LogArchiveRecord addObject(final LogObjectValue object) {
    objectsProperty.add().copy(object);
    return this;
  }

  @Override
  public ArchivedSnapshotValue getSnapshot() {
    return snapshotProperty.getValue();
  }

  public LogArchiveRecord setSnapshot(final ArchivedSnapshotValue snapshot) {
    snapshotProperty.getValue().copy(snapshot);
    return this;
  }

  @Override
  public IndexChunkValue getChunk() {
    return chunkProperty.getValue();
  }

  public LogArchiveRecord setChunk(final IndexChunkValue chunk) {
    chunkProperty.getValue().copy(chunk);
    return this;
  }

  @Override
  public long getPublishedSeq() {
    return publishedSeqProperty.getValue();
  }

  public LogArchiveRecord setPublishedSeq(final long publishedSeq) {
    publishedSeqProperty.setValue(publishedSeq);
    return this;
  }

  @Override
  public long getArchivedIndex() {
    return archivedIndexProperty.getValue();
  }

  public LogArchiveRecord setArchivedIndex(final long archivedIndex) {
    archivedIndexProperty.setValue(archivedIndex);
    return this;
  }

  @Override
  public long getArchivedPosition() {
    return archivedPositionProperty.getValue();
  }

  public LogArchiveRecord setArchivedPosition(final long archivedPosition) {
    archivedPositionProperty.setValue(archivedPosition);
    return this;
  }

  /**
   * Copies each element, because the elements of the underlying array are reused while iterating.
   */
  @Override
  public List<CoveredCheckpointValue> getCheckpoints() {
    return checkpointsProperty.stream()
        .map(element -> (CoveredCheckpointValue) new CoveredCheckpoint().copy(element))
        .toList();
  }

  public LogArchiveRecord addCheckpoint(final CoveredCheckpointValue checkpoint) {
    checkpointsProperty.add().copy(checkpoint);
    return this;
  }
}
