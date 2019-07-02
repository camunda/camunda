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

public class JobMetrics {

  private static final Counter JOB_EVENTS =
      Counter.build()
          .namespace("zeebe")
          .name("job_events_total")
          .help("Number of job events")
          .labelNames("action", "partition")
          .register();

  private static final Gauge PENDING_JOBS =
      Gauge.build()
          .namespace("zeebe")
          .name("pending_jobs_total")
          .help("Number of pending jobs")
          .labelNames("partition")
          .register();

  private final String partitionIdLabel;

  public JobMetrics(int partitionId) {
    this.partitionIdLabel = String.valueOf(partitionId);
  }

  private void jobEvent(String action) {
    JOB_EVENTS.labels(action, partitionIdLabel).inc();
  }

  public void jobCreated() {
    jobEvent("created");
    PENDING_JOBS.labels(partitionIdLabel).inc();
  }

  private void jobFinished() {
    PENDING_JOBS.labels(partitionIdLabel).dec();
  }

  public void jobActivated() {
    jobEvent("activated");
  }

  public void jobTimedOut() {
    jobEvent("timed out");
  }

  public void jobCompleted() {
    jobEvent("completed");
    jobFinished();
  }

  public void jobFailed() {
    jobEvent("failed");
  }

  public void jobCanceled() {
    jobEvent("canceled");
    jobFinished();
  }
}
