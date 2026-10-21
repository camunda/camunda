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
  private final LongProperty handoverCheckpointIdProperty =
      new LongProperty("handoverCheckpointId", -1L);
  private final LongProperty handoverPositionProperty = new LongProperty("handoverPosition", -1L);
  private final LongProperty mergedIndexProperty = new LongProperty("mergedIndex", -1L);
  private final LongProperty mergedPositionProperty = new LongProperty("mergedPosition", -1L);
  private final LongProperty releasedIndexProperty = new LongProperty("releasedIndex", -1L);
  private final LongProperty releasedPositionProperty = new LongProperty("releasedPosition", -1L);

  public LogArchiveMeta() {
    super(15);
    declareProperty(timelineIdProperty)
        .declareProperty(parentTimelineIdProperty)
        .declareProperty(startIndexProperty)
        .declareProperty(archivedIndexProperty)
        .declareProperty(archivedPositionProperty)
        .declareProperty(nextSeqProperty)
        .declareProperty(lastPublishedSeqProperty)
        .declareProperty(nextChunkNoProperty)
        .declareProperty(firstArchivedPositionProperty)
        .declareProperty(handoverCheckpointIdProperty)
        .declareProperty(handoverPositionProperty)
        .declareProperty(mergedIndexProperty)
        .declareProperty(mergedPositionProperty)
        .declareProperty(releasedIndexProperty)
        .declareProperty(releasedPositionProperty);
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

  /** The id of the handover checkpoint H, -1 if no handover is recorded on this timeline. */
  public long getHandoverCheckpointId() {
    return handoverCheckpointIdProperty.getValue();
  }

  public LogArchiveMeta setHandoverCheckpointId(final long handoverCheckpointId) {
    handoverCheckpointIdProperty.setValue(handoverCheckpointId);
    return this;
  }

  /** The log position of the handover checkpoint H, -1 if no handover is recorded. */
  public long getHandoverPosition() {
    return handoverPositionProperty.getValue();
  }

  public LogArchiveMeta setHandoverPosition(final long handoverPosition) {
    handoverPositionProperty.setValue(handoverPosition);
    return this;
  }

  /**
   * M, the highest merged journal index: every archived object up to it was replaced by a merged
   * object. {@code startIndex - 1} while nothing is merged.
   */
  public long getMergedIndex() {
    return mergedIndexProperty.getValue();
  }

  public LogArchiveMeta setMergedIndex(final long mergedIndex) {
    mergedIndexProperty.setValue(mergedIndex);
    return this;
  }

  /** M as a log position, -1 until a merged object with application entries exists. */
  public long getMergedPosition() {
    return mergedPositionProperty.getValue();
  }

  public LogArchiveMeta setMergedPosition(final long mergedPosition) {
    mergedPositionProperty.setValue(mergedPosition);
    return this;
  }

  /**
   * R, the highest released journal index: every object up to it belongs to the event plane. {@code
   * startIndex - 1} while nothing is released.
   */
  public long getReleasedIndex() {
    return releasedIndexProperty.getValue();
  }

  public LogArchiveMeta setReleasedIndex(final long releasedIndex) {
    releasedIndexProperty.setValue(releasedIndex);
    return this;
  }

  /** R as a log position, -1 until a released object with application entries exists. */
  public long getReleasedPosition() {
    return releasedPositionProperty.getValue();
  }

  public LogArchiveMeta setReleasedPosition(final long releasedPosition) {
    releasedPositionProperty.setValue(releasedPosition);
    return this;
  }

  /** Whether a handover is recorded on this timeline. */
  public boolean hasHandover() {
    return getHandoverCheckpointId() >= 0;
  }

  /**
   * Whether the checkpoint is a restore point of the log archive only: a handover is recorded and
   * the checkpoint is newer than the handover checkpoint H. No old-style backup exists for it.
   */
  public boolean isAfterHandover(final long checkpointId) {
    return hasHandover() && checkpointId > getHandoverCheckpointId();
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
        .setFirstArchivedPosition(getFirstArchivedPosition())
        .setHandoverCheckpointId(getHandoverCheckpointId())
        .setHandoverPosition(getHandoverPosition())
        .setMergedIndex(getMergedIndex())
        .setMergedPosition(getMergedPosition())
        .setReleasedIndex(getReleasedIndex())
        .setReleasedPosition(getReleasedPosition());
  }

  @Override
  public String toString() {
    return "LogArchiveMeta{timelineId=%s, parentTimelineId=%s, startIndex=%d, archivedIndex=%d, archivedPosition=%d, nextSeq=%d, lastPublishedSeq=%d, nextChunkNo=%d, firstArchivedPosition=%d, handoverCheckpointId=%d, handoverPosition=%d, mergedIndex=%d, mergedPosition=%d, releasedIndex=%d, releasedPosition=%d}"
        .formatted(
            getTimelineId(),
            getParentTimelineId(),
            getStartIndex(),
            getArchivedIndex(),
            getArchivedPosition(),
            getNextSeq(),
            getLastPublishedSeq(),
            getNextChunkNo(),
            getFirstArchivedPosition(),
            getHandoverCheckpointId(),
            getHandoverPosition(),
            getMergedIndex(),
            getMergedPosition(),
            getReleasedIndex(),
            getReleasedPosition());
  }
}
