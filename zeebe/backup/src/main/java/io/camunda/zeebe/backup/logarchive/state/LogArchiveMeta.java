/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.state;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * The single row of {@code LOG_ARCHIVE_META} (spec section 5). A freshly created instance is the
 * state before any timeline was started: empty timeline id, nothing archived.
 *
 * <p>Instances returned by {@link DbLogArchiveState#meta()} are copies and can be kept and modified
 * freely; they only reach the state through {@link DbLogArchiveState#setMeta}.
 */
public final class LogArchiveMeta extends UnpackedObject implements DbValue {

  private final StringProperty timelineIdProperty = new StringProperty("timelineId", "");
  private final StringProperty parentTimelineIdProperty =
      new StringProperty("parentTimelineId", "");
  private final LongProperty startIndexProperty = new LongProperty("startIndex", -1L);
  private final LongProperty archivedIndexProperty = new LongProperty("archivedIndex", -1L);
  private final LongProperty archivedPositionProperty = new LongProperty("archivedPosition", -1L);
  private final LongProperty nextSeqProperty = new LongProperty("nextSeq", 1L);
  private final LongProperty lastPublishedSeqProperty = new LongProperty("lastPublishedSeq", 0L);
  private final LongProperty nextChunkNoProperty = new LongProperty("nextChunkNo", 1L);
  private final LongProperty firstArchivedPositionProperty =
      new LongProperty("firstArchivedPosition", -1L);

  public LogArchiveMeta() {
    super(9);
    declareProperty(timelineIdProperty)
        .declareProperty(parentTimelineIdProperty)
        .declareProperty(startIndexProperty)
        .declareProperty(archivedIndexProperty)
        .declareProperty(archivedPositionProperty)
        .declareProperty(nextSeqProperty)
        .declareProperty(lastPublishedSeqProperty)
        .declareProperty(nextChunkNoProperty)
        .declareProperty(firstArchivedPositionProperty);
  }

  /** The id of the current timeline, empty if no timeline was started yet. */
  public String getTimelineId() {
    return BufferUtil.bufferAsString(timelineIdProperty.getValue());
  }

  public LogArchiveMeta setTimelineId(final String timelineId) {
    timelineIdProperty.setValue(timelineId);
    return this;
  }

  public String getParentTimelineId() {
    return BufferUtil.bufferAsString(parentTimelineIdProperty.getValue());
  }

  public LogArchiveMeta setParentTimelineId(final String parentTimelineId) {
    parentTimelineIdProperty.setValue(parentTimelineId);
    return this;
  }

  public long getStartIndex() {
    return startIndexProperty.getValue();
  }

  public LogArchiveMeta setStartIndex(final long startIndex) {
    startIndexProperty.setValue(startIndex);
    return this;
  }

  /** F, the highest archived journal index. {@code startIndex - 1} while nothing is archived. */
  public long getArchivedIndex() {
    return archivedIndexProperty.getValue();
  }

  public LogArchiveMeta setArchivedIndex(final long archivedIndex) {
    archivedIndexProperty.setValue(archivedIndex);
    return this;
  }

  /** F as a log position, -1 until the first application entry is archived. */
  public long getArchivedPosition() {
    return archivedPositionProperty.getValue();
  }

  public LogArchiveMeta setArchivedPosition(final long archivedPosition) {
    archivedPositionProperty.setValue(archivedPosition);
    return this;
  }

  public long getNextSeq() {
    return nextSeqProperty.getValue();
  }

  public LogArchiveMeta setNextSeq(final long nextSeq) {
    nextSeqProperty.setValue(nextSeq);
    return this;
  }

  public long getLastPublishedSeq() {
    return lastPublishedSeqProperty.getValue();
  }

  public LogArchiveMeta setLastPublishedSeq(final long lastPublishedSeq) {
    lastPublishedSeqProperty.setValue(lastPublishedSeq);
    return this;
  }

  public long getNextChunkNo() {
    return nextChunkNoProperty.getValue();
  }

  public LogArchiveMeta setNextChunkNo(final long nextChunkNo) {
    nextChunkNoProperty.setValue(nextChunkNo);
    return this;
  }

  /** Position of the first application entry ever archived on this timeline, -1 if none. */
  public long getFirstArchivedPosition() {
    return firstArchivedPositionProperty.getValue();
  }

  public LogArchiveMeta setFirstArchivedPosition(final long firstArchivedPosition) {
    firstArchivedPositionProperty.setValue(firstArchivedPosition);
    return this;
  }

  /** Returns an independent copy. */
  public LogArchiveMeta copy() {
    return new LogArchiveMeta()
        .setTimelineId(getTimelineId())
        .setParentTimelineId(getParentTimelineId())
        .setStartIndex(getStartIndex())
        .setArchivedIndex(getArchivedIndex())
        .setArchivedPosition(getArchivedPosition())
        .setNextSeq(getNextSeq())
        .setLastPublishedSeq(getLastPublishedSeq())
        .setNextChunkNo(getNextChunkNo())
        .setFirstArchivedPosition(getFirstArchivedPosition());
  }

  @Override
  public String toString() {
    return "LogArchiveMeta{timelineId=%s, parentTimelineId=%s, startIndex=%d, archivedIndex=%d, archivedPosition=%d, nextSeq=%d, lastPublishedSeq=%d, nextChunkNo=%d, firstArchivedPosition=%d}"
        .formatted(
            getTimelineId(),
            getParentTimelineId(),
            getStartIndex(),
            getArchivedIndex(),
            getArchivedPosition(),
            getNextSeq(),
            getLastPublishedSeq(),
            getNextChunkNo(),
            getFirstArchivedPosition());
  }
}
