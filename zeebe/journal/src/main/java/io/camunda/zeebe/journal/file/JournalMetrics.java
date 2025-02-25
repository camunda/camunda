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

import static io.camunda.zeebe.journal.file.JournalMetricsDoc.*;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;

final class JournalMetrics {
  private final Timer segmentCreationTime;
  private final Timer segmentTruncateTime;
  private final Timer segmentFlushTime;
  private final Timer journalFlushTime;
  private final AtomicLong segmentCount;
  private final AtomicLong journalOpenDuration;
  private final Timer segmentAllocationTime;
  private final Timer appendLatency;
  private final Counter appendRate;
  private final Counter appendDataRate;
  private final Timer seekLatency;
  private final MeterRegistry registry;

  JournalMetrics(final MeterRegistry registry) {
    this.registry = registry;
    segmentCreationTime = makeTimer(SEGMENT_CREATION_TIME);
    segmentTruncateTime = makeTimer(SEGMENT_TRUNCATE_TIME);
    segmentFlushTime = makeTimer(SEGMENT_FLUSH_TIME);
    journalFlushTime = makeTimer(JOURNAL_FLUSH_TIME);

    segmentCount = new AtomicLong(0L);
    Gauge.builder(SEGMENT_COUNT.getName(), segmentCount::get)
        .description(SEGMENT_COUNT.getDescription())
        .register(registry);

    journalOpenDuration = new AtomicLong(0L);
    Gauge.builder(JOURNAL_OPERATION_DURATION.getName(), journalOpenDuration::get)
        .description(JOURNAL_OPERATION_DURATION.getDescription())
        .register(registry);

    segmentAllocationTime = makeTimer(SEGMENT_ALLOCATION_TIME);
    appendLatency = makeTimer(APPEND_LATENCY);
    appendRate =
        Counter.builder(APPEND_RATE.getName())
            .description(APPEND_RATE.getDescription())
            .register(registry);
    appendDataRate =
        Counter.builder(APPEND_DATA_RATE.getName())
            .description(APPEND_DATA_RATE.getDescription())
            .register(registry);
    seekLatency = makeTimer(SEEK_LATENCY);
  }

  void observeSegmentCreation(final Runnable segmentCreation) {
    segmentCreationTime.record(segmentCreation);
  }

  CloseableSilently observeSegmentFlush() {
    return MicrometerUtil.timer(segmentFlushTime, Timer.start(registry));
  }

  CloseableSilently observeJournalFlush() {
    return MicrometerUtil.timer(journalFlushTime, Timer.start(registry));
  }

  void observeSegmentTruncation(final Runnable segmentTruncation) {
    segmentTruncateTime.record(segmentTruncation);
  }

  CloseableSilently startJournalOpenDurationTimer() {
    final var now = registry.config().clock().monotonicTime();
    return () -> {
      final var end = registry.config().clock().monotonicTime();
      journalOpenDuration.set(end - now);
    };
  }

  void incSegmentCount() {
    segmentCount.incrementAndGet();
  }

  void decSegmentCount() {
    segmentCount.decrementAndGet();
  }

  CloseableSilently observeSegmentAllocation() {
    return MicrometerUtil.timer(segmentAllocationTime, Timer.start(registry));
  }

  void observeAppend(final long appendedBytes) {
    appendRate.increment();
    appendDataRate.increment(appendedBytes / 1024f);
  }

  CloseableSilently observeAppendLatency() {
    return MicrometerUtil.timer(appendLatency, Timer.start(registry));
  }

  CloseableSilently observeSeekLatency() {
    return MicrometerUtil.timer(seekLatency, Timer.start(registry));
  }

  private Timer makeTimer(final JournalMetricsDoc meter) {
    return Timer.builder(meter.getName())
        .description(meter.getDescription())
        .serviceLevelObjectives(meter.getTimerSLOs())
        .register(registry);
  }
}
