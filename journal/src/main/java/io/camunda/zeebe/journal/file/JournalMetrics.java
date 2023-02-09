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
package io.camunda.zeebe.journal.file;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Gauge.Timer;
import io.prometheus.client.Histogram;

final class JournalMetrics {
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

  private static final Histogram SEGMENT_ALLOCATION_TIME =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("segment_allocation_time")
          .help("Time spent to allocate a new segment")
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Counter APPEND_DATA_RATE =
      Counter.build()
          .namespace(NAMESPACE)
          .name("journal_append_data_rate")
          .help("The rate in KiB at which we append data to the journal")
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Counter APPEND_RATE =
      Counter.build()
          .namespace(NAMESPACE)
          .name("journal_append_rate")
          .help("The rate at which we append entries in the journal, by entry count")
          .labelNames(PARTITION_LABEL)
          .register();

  private static final Histogram APPEND_LATENCY =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("journal_append_latency")
          .help("Distribution of time spent appending journal records, excluding flushing")
          .labelNames(PARTITION_LABEL)
          .register();

  private final Histogram.Child segmentCreationTime;
  private final Histogram.Child segmentTruncateTime;
  private final Histogram.Child segmentFlushTime;
  private final Gauge.Child segmentCount;
  private final Gauge.Child journalOpenTime;
  private final Histogram.Child segmentAllocationTime;
  private final Histogram.Child appendLatency;
  private final Counter.Child appendRate;
  private final Counter.Child appendDataRate;

  JournalMetrics(final String partitionId) {
    segmentCreationTime = SEGMENT_CREATION_TIME.labels(partitionId);
    segmentTruncateTime = SEGMENT_TRUNCATE_TIME.labels(partitionId);
    segmentFlushTime = SEGMENT_FLUSH_TIME.labels(partitionId);
    segmentCount = SEGMENT_COUNT.labels(partitionId);
    journalOpenTime = JOURNAL_OPEN_DURATION.labels(partitionId);
    segmentAllocationTime = SEGMENT_ALLOCATION_TIME.labels(partitionId);
    appendLatency = APPEND_LATENCY.labels(partitionId);
    appendRate = APPEND_RATE.labels(partitionId);
    appendDataRate = APPEND_DATA_RATE.labels(partitionId);
  }

  void observeSegmentCreation(final Runnable segmentCreation) {
    segmentCreationTime.time(segmentCreation);
  }

  void observeSegmentFlush(final Runnable segmentFlush) {
    segmentFlushTime.time(segmentFlush);
  }

  void observeSegmentTruncation(final Runnable segmentTruncation) {
    segmentTruncateTime.time(segmentTruncation);
  }

  Timer startJournalOpenDurationTimer() {
    return journalOpenTime.startTimer();
  }

  void incSegmentCount() {
    segmentCount.inc();
  }

  void decSegmentCount() {
    segmentCount.dec();
  }

  Histogram.Timer observeSegmentAllocation() {
    return segmentAllocationTime.startTimer();
  }

  void observeAppend(final long appendedBytes) {
    appendRate.inc();
    appendDataRate.inc(appendedBytes / 1024f);
  }

  Histogram.Timer observeAppendLatency() {
    return appendLatency.startTimer();
  }
}
