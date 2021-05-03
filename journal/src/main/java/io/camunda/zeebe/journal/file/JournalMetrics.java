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
package io.zeebe.journal.file;

import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

class JournalMetrics {
  private static final String NAMESPACE = "atomix";
  private static final String PARTITION_LABEL = "partition";
  private static final Histogram SEGMENT_CREATION_TIME =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("segment_creation_time")
          .help("Time spend to create a new segment")
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Histogram SEGMENT_TRUNCATE_TIME =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("segment_truncate_time")
          .help("Time spend to truncate a segment")
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Histogram SEGMENT_FLUSH_TIME =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("segment_flush_time")
          .help("Time spend to flush segment to disk")
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Gauge SEGMENT_COUNT =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("segment_count")
          .help("Number of segments")
          .labelNames(PARTITION_LABEL)
          .register();
  private static final Gauge JOURNAL_OPEN_DURATION =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("journal_open_time")
          .help("Time taken to open the journal")
          .labelNames(PARTITION_LABEL)
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

  public void observeJournalOpenDuration(final long durationMillis) {
    JOURNAL_OPEN_DURATION.labels(logName).set(durationMillis / 1000f);
  }

  public void incSegmentCount() {
    SEGMENT_COUNT.labels(logName).inc();
  }

  public void decSegmentCount() {
    SEGMENT_COUNT.labels(logName).dec();
  }
}
