/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.storage.statistics;

import io.prometheus.client.Histogram;

public class JournalMetrics {

  private static final Histogram SEGMENT_CREATION_TIME =
      Histogram.build()
          .namespace("atomix")
          .name("segment_creation_time")
          .help("Time spend to create a new segment")
          .labelNames("partition")
          .register();

  private static final Histogram SEGMENT_TRUNCATE_TIME =
      Histogram.build()
          .namespace("atomix")
          .name("segment_truncate_time")
          .help("Time spend to truncate a segment")
          .labelNames("partition")
          .register();

  private static final Histogram SEGMENT_FLUSH_TIME =
      Histogram.build()
          .namespace("atomix")
          .name("segment_flush_time")
          .help("Time spend to flush segment to disk")
          .labelNames("partition")
          .register();

  private final String logName;

  public JournalMetrics(final String logName) {
    this.logName = logName;
  }

  public void observeSegmentCreation(final Runnable segmentCreation) {
    SEGMENT_CREATION_TIME.labels(logName).time(segmentCreation);
  }

  public void observeSegmentFlush(final Runnable segmentFlush) {
    SEGMENT_FLUSH_TIME.labels(logName).time(segmentFlush);
  }

  public void observeSegmentTruncation(final Runnable segmentTruncation) {
    SEGMENT_TRUNCATE_TIME.labels(logName).time(segmentTruncation);
  }
}
