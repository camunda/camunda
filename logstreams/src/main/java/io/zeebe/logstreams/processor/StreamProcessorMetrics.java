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
package io.zeebe.logstreams.processor;

import io.zeebe.util.metrics.Metric;
import io.zeebe.util.metrics.MetricsManager;

public class StreamProcessorMetrics {
  private final Metric eventsProcessedCountMetric;
  private final Metric eventsWrittenCountMetric;
  private final Metric eventsSkippedCountMetric;
  private final Metric snapshotSizeMetric;
  private final Metric snapshotTimeMillisMetric;

  public StreamProcessorMetrics(
      final MetricsManager metricsManager, final String processorName, final String partitionId) {
    eventsProcessedCountMetric =
        metricsManager
            .newMetric("streamprocessor_events_count")
            .type("counter")
            .label("processor", processorName)
            .label("action", "processed")
            .label("partition", partitionId)
            .create();

    eventsWrittenCountMetric =
        metricsManager
            .newMetric("streamprocessor_events_count")
            .type("counter")
            .label("processor", processorName)
            .label("action", "written")
            .label("partition", partitionId)
            .create();

    eventsSkippedCountMetric =
        metricsManager
            .newMetric("streamprocessor_events_count")
            .type("counter")
            .label("processor", processorName)
            .label("action", "skipped")
            .label("partition", partitionId)
            .create();

    snapshotSizeMetric =
        metricsManager
            .newMetric("streamprocessor_snapshot_last_size_bytes")
            .type("gauge")
            .label("processor", processorName)
            .label("partition", partitionId)
            .create();

    snapshotTimeMillisMetric =
        metricsManager
            .newMetric("streamprocessor_snapshot_last_duration_millis")
            .type("gauge")
            .label("processor", processorName)
            .label("partition", partitionId)
            .create();
  }

  public void close() {
    eventsProcessedCountMetric.close();
    eventsSkippedCountMetric.close();
    eventsWrittenCountMetric.close();
    snapshotTimeMillisMetric.close();
    snapshotSizeMetric.close();
  }

  public void incrementEventsProcessedCount() {
    eventsProcessedCountMetric.incrementOrdered();
  }

  public void incrementEventsSkippedCount() {
    eventsSkippedCountMetric.incrementOrdered();
  }

  public void incrementEventsWrittenCount() {
    eventsWrittenCountMetric.incrementOrdered();
  }

  public void recordSnapshotSize(final long size) {
    snapshotSizeMetric.setOrdered(size);
  }

  public void recordSnapshotCreationTime(final long creationTime) {
    snapshotTimeMillisMetric.setOrdered(creationTime);
  }
}
