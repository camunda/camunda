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
package io.zeebe.engine.processor.workflow;

import io.zeebe.util.metrics.Metric;
import io.zeebe.util.metrics.MetricsManager;

public class WorkflowInstanceMetrics implements AutoCloseable {
  private final Metric workflowInstanceEventCreated;
  private final Metric workflowInstanceEventCanceled;
  private final Metric workflowInstanceEventCompleted;

  public WorkflowInstanceMetrics(final MetricsManager metricsManager, final int partitionId) {
    final String partitionIdString = Integer.toString(partitionId);

    workflowInstanceEventCanceled =
        metricsManager
            .newMetric("workflow_instance_events_count")
            .type("counter")
            .label("partition", partitionIdString)
            .label("type", "canceled")
            .create();

    workflowInstanceEventCompleted =
        metricsManager
            .newMetric("workflow_instance_events_count")
            .type("counter")
            .label("partition", partitionIdString)
            .label("type", "completed")
            .create();

    workflowInstanceEventCreated =
        metricsManager
            .newMetric("workflow_instance_events_count")
            .type("counter")
            .label("partition", partitionIdString)
            .label("type", "created")
            .create();
  }

  public void countInstanceCanceled() {
    workflowInstanceEventCanceled.incrementOrdered();
  }

  public void countInstanceCompleted() {
    workflowInstanceEventCompleted.incrementOrdered();
  }

  public void countInstanceCreated() {
    workflowInstanceEventCreated.incrementOrdered();
  }

  @Override
  public void close() {
    workflowInstanceEventCreated.close();
    workflowInstanceEventCanceled.close();
    workflowInstanceEventCompleted.close();
  }
}
