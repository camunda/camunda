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
package io.zeebe.engine.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public class IncidentMetrics {

  private static final Counter INCIDENT_EVENTS =
      Counter.build()
          .namespace("zeebe")
          .name("incident_events_total")
          .help("Number of incident events")
          .labelNames("action", "partition")
          .register();

  private static final Gauge PENDING_INCIDENTS =
      Gauge.build()
          .namespace("zeebe")
          .name("pending_incidents_total")
          .help("Number of pending incidents")
          .labelNames("partition")
          .register();

  private final String partitionIdLabel;

  public IncidentMetrics(int partitionId) {
    this.partitionIdLabel = String.valueOf(partitionId);
  }

  private void incidentEvent(String action) {
    INCIDENT_EVENTS.labels(action, partitionIdLabel).inc();
  }

  public void incidentCreated() {
    incidentEvent("created");
    PENDING_INCIDENTS.labels(partitionIdLabel).inc();
  }

  public void incidentResolved() {
    incidentEvent("resolved");
    PENDING_INCIDENTS.labels(partitionIdLabel).dec();
  }
}
