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
import io.zeebe.protocol.record.value.BpmnElementType;

public class WorkflowEngineMetrics {

  private static final Counter ELEMENT_INSTANCE_EVENTS =
      Counter.build()
          .namespace("zeebe")
          .name("element_instance_events_total")
          .help("Number of workflow element instance events")
          .labelNames("action", "type", "partition")
          .register();

  private static final Gauge RUNNING_WORKFLOW_INSTANCES =
      Gauge.build()
          .namespace("zeebe")
          .name("running_workflow_instances_total")
          .help("Number of running workflow instances")
          .labelNames("partition")
          .register();

  private final String partitionIdLabel;

  public WorkflowEngineMetrics(int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  private void elementInstanceEvent(String action, BpmnElementType elementType) {
    ELEMENT_INSTANCE_EVENTS.labels(action, elementType.name(), partitionIdLabel).inc();
  }

  private void workflowInstanceCreated() {
    RUNNING_WORKFLOW_INSTANCES.labels(partitionIdLabel).inc();
  }

  private void workflowInstanceFinished() {
    RUNNING_WORKFLOW_INSTANCES.labels(partitionIdLabel).dec();
  }

  public void elementInstanceActivated(BpmnElementType elementType) {
    elementInstanceEvent("activated", elementType);

    if (isWorkflowInstance(elementType)) {
      workflowInstanceCreated();
    }
  }

  public void elementInstanceCompleted(BpmnElementType elementType) {
    elementInstanceEvent("completed", elementType);

    if (isWorkflowInstance(elementType)) {
      workflowInstanceFinished();
    }
  }

  public void elementInstanceTerminated(BpmnElementType elementType) {
    elementInstanceEvent("terminated", elementType);

    if (isWorkflowInstance(elementType)) {
      workflowInstanceFinished();
    }
  }

  private boolean isWorkflowInstance(BpmnElementType elementType) {
    return BpmnElementType.PROCESS == elementType;
  }
}
