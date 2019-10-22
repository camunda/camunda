/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log.fs;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FsLogSegments {

  private static final String ERROR_ALREADY_CLOSED =
      "Expected to have at least one open segment, but seem to be closed already.";

  protected CopyOnWriteArrayList<FsLogSegment> segments;

  private FsLogSegments(Collection<FsLogSegment> initialSegments) {
    this.segments = new CopyOnWriteArrayList<>(initialSegments);
  }

  public static FsLogSegments fromFsLogSegmentsArray(Collection<FsLogSegment> initialSegments) {
    if (initialSegments.isEmpty()) {
      throw new IllegalArgumentException(
          "Expect at least one open FsLogSegment to create FsLogSegments.");
    }

    return new FsLogSegments(initialSegments);
  }

  /** invoked by the conductor after a new segment has been allocated */
  public void addSegment(FsLogSegment segment) {
    segments.add(segment);
  }

  public FsLogSegment getSegment(int segmentId) {
    final int firstSegmentId = getFirstSegmentId();
    final int segmentIdx = segmentId - firstSegmentId;

    if (0 <= segmentIdx && segmentIdx < segments.size()) {
      return segments.get(segmentIdx);
    } else {
      return null;
    }
  }

  public void deleteSegmentsUntil(int segmentId) {
    int firstSegmentId = getFirstSegmentId();
    if (segmentId < firstSegmentId) {
      return;
    }

    // we need to remove the segment from the collection as first
    // before closing it - otherwise other threads will see the segment still in the
    // collection when it is already closed (for example if we use #removeIf)
    // Note: Iterator#remove is not implemented by CopyOnWriteArrayList
    while (firstSegmentId != segmentId) {
      final FsLogSegment segment = segments.remove(0);
      segment.closeSegment();
      segment.delete();

      firstSegmentId = getFirstSegmentId();
    }
  }

  public int getFirstSegmentId() {
    if (segments.isEmpty()) {
      throw new IllegalStateException(ERROR_ALREADY_CLOSED);
    }

    return segments.get(0).getSegmentId();
  }

  public void closeAll() {
    final FsLogSegment[] fsLogSegments = segments.toArray(new FsLogSegment[0]);
    segments.clear();

    for (FsLogSegment segment : fsLogSegments) {
      segment.closeSegment();
    }
  }

  public FsLogSegment getFirst() {
    if (segments.isEmpty()) {
      throw new IllegalStateException(ERROR_ALREADY_CLOSED);
    }

    return segments.get(0);
  }

  public int getLastSegmentId() {
    if (segments.isEmpty()) {
      throw new IllegalStateException(ERROR_ALREADY_CLOSED);
    }

    final int size = segments.size();
    return segments.get(size - 1).getSegmentId();
  }
}
