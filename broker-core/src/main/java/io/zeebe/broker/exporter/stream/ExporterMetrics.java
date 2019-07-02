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

import io.prometheus.client.Counter;

public class ExporterMetrics {

  private static final Counter EXPORTER_EVENTS =
      Counter.build()
          .namespace("zeebe")
          .name("exporter_events_total")
          .help("Number of events processed by exporter")
          .labelNames("action", "partition")
          .register();

  private final String partitionIdLabel;

  public ExporterMetrics(int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  private void event(String action) {
    EXPORTER_EVENTS.labels(action, partitionIdLabel).inc();
  }

  public void eventExported() {
    event("exported");
  }

  public void eventSkipped() {
    event("skipped");
  }
}
