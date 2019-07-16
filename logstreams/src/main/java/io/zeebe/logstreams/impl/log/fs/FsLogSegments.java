/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log.fs;

public class FsLogSegments {
  protected int initialSegmentId = -1;

  protected FsLogSegment[] segments = new FsLogSegment[0];

  protected volatile int segmentCount = 0;

  public void init(int initialSegmentId, FsLogSegment[] initialSegments) {
    this.segments = initialSegments;
    this.initialSegmentId = initialSegmentId;
    this.segmentCount = initialSegments.length; // volatile store
  }

  /** invoked by the conductor after a new segment has been allocated */
  public void addSegment(FsLogSegment segment) {
    final FsLogSegment[] newSegments = new FsLogSegment[segments.length + 1];

    System.arraycopy(segments, 0, newSegments, 0, segments.length);
    newSegments[segments.length] = segment;
    this.segments = newSegments;

    this.segmentCount = newSegments.length; // volatile store
  }

  public void removeSegmentsUntil(int segmentId) {
    final int segmentIdx = segmentId - initialSegmentId;
    final int newLength = segments.length - segmentIdx;
    final FsLogSegment[] newSegments = new FsLogSegment[newLength];

    System.arraycopy(segments, segmentIdx, newSegments, 0, newLength);
    this.segments = newSegments;
    initialSegmentId += segmentIdx;
    this.segmentCount = newSegments.length; // volatile store
  }

  public FsLogSegment getSegment(int segmentId) {
    final int segmentCount = this.segmentCount; // volatile load

    final FsLogSegment[] segments = this.segments;

    final int segmentIdx = segmentId - initialSegmentId;

    if (0 <= segmentIdx && segmentIdx < segmentCount) {
      return segments[segmentIdx];
    } else {
      return null;
    }
  }

  public FsLogSegment getFirst() {
    if (segmentCount > 0) {
      return segments[0];
    } else {
      return null;
    }
  }

  public void closeAll() {
    final FsLogSegment[] segments = this.segments;
    for (FsLogSegment readableLogSegment : segments) {
      readableLogSegment.closeSegment();
    }

    this.segments = new FsLogSegment[0];
    this.segmentCount = 0;
  }

  public int getLastSegmentId() {
    return initialSegmentId + (segmentCount - 1);
  }

  public int getSegmentCount() {
    return segmentCount;
  }
}
