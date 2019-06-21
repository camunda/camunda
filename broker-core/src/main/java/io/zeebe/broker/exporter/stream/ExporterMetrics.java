/*
 * Zeebe Broker Core
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
package io.zeebe.broker.exporter.stream;

import io.zeebe.util.metrics.Metric;
import io.zeebe.util.metrics.MetricsManager;

public class ExporterMetrics {
  private final Metric eventsExportedCountMetric;
  private final Metric eventsSkippedCountMetric;

  public ExporterMetrics(final MetricsManager metricsManager, final String partitionId) {
    eventsExportedCountMetric =
        metricsManager
            .newMetric("exporter_events_count")
            .type("counter")
            .label("action", "exported")
            .label("partition", partitionId)
            .create();

    eventsSkippedCountMetric =
        metricsManager
            .newMetric("exporter_events_count")
            .type("counter")
            .label("action", "skipped")
            .label("partition", partitionId)
            .create();
  }

  public void close() {
    eventsExportedCountMetric.close();
    eventsSkippedCountMetric.close();
  }

  public void incrementEventsExportedCount() {
    eventsExportedCountMetric.incrementOrdered();
  }

  public void incrementEventsSkippedCount() {
    eventsSkippedCountMetric.incrementOrdered();
  }
}
