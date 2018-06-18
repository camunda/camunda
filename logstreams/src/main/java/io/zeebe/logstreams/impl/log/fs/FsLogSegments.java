/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.impl.log.fs;

public class FsLogSegments {
  protected int initalSegmentId = -1;

  protected FsLogSegment[] segments = new FsLogSegment[0];

  protected volatile int segmentCount = 0;

  public void init(int initalSegmentId, FsLogSegment[] initialSegments) {
    this.segments = initialSegments;
    this.initalSegmentId = initalSegmentId;
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

  public FsLogSegment getSegment(int segmentId) {
    final int segmentCount = this.segmentCount; // volatile load

    final FsLogSegment[] segments = this.segments;

    final int segmentIdx = segmentId - initalSegmentId;

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

  public int getSegmentCount() {
    return segmentCount;
  }
}
