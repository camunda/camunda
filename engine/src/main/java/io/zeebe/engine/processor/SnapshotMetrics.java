/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor;

import io.zeebe.util.metrics.Metric;
import io.zeebe.util.metrics.MetricsManager;

public class SnapshotMetrics {
  private final Metric snapshotSizeMetric;
  private final Metric snapshotTimeMillisMetric;

  public SnapshotMetrics(
      final MetricsManager metricsManager, final String processorName, final String partitionId) {
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
    snapshotTimeMillisMetric.close();
    snapshotSizeMetric.close();
  }

  public void recordSnapshotSize(final long size) {
    snapshotSizeMetric.setOrdered(size);
  }

  public void recordSnapshotCreationTime(final long creationTime) {
    snapshotTimeMillisMetric.setOrdered(creationTime);
  }
}
