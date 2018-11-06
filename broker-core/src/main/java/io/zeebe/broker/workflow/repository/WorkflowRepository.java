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
package io.zeebe.broker.workflow.repository;

import static io.zeebe.broker.workflow.WorkflowServiceNames.WORKFLOW_REPOSITORY_SERVICE;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandlerManager;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.ServerTransport;

public class WorkflowRepository implements StreamProcessorLifecycleAware {

  private final ListWorkflowsControlMessageHandler listWorkflowsControlMessageHandler;
  private final GetWorkflowControlMessageHandler getWorkflowMessageHandler;
  private final ServiceStartContext startContext;
  private final WorkflowState workflowState;
  private final ServiceName<Partition> partitionServiceName;

  public WorkflowRepository(
      ServerTransport clientApiTransport,
      ControlMessageHandlerManager controlMessageHandlerManager,
      ServiceStartContext startContext,
      WorkflowState workflowState,
      ServiceName<Partition> partitionServiceName) {
    this.startContext = startContext;
    this.workflowState = workflowState;
    this.partitionServiceName = partitionServiceName;

    getWorkflowMessageHandler =
        new GetWorkflowControlMessageHandler(clientApiTransport.getOutput());
    listWorkflowsControlMessageHandler =
        new ListWorkflowsControlMessageHandler(clientApiTransport.getOutput());

    controlMessageHandlerManager.registerHandler(getWorkflowMessageHandler);
    controlMessageHandlerManager.registerHandler(listWorkflowsControlMessageHandler);
  }

  @Override
  public void onRecovered(TypedStreamProcessor streamProcessor) {
    final WorkflowRepositoryService workflowRepositoryService =
        new WorkflowRepositoryService(streamProcessor.getActor(), workflowState);

    startContext
        .createService(WORKFLOW_REPOSITORY_SERVICE, workflowRepositoryService)
        .dependency(partitionServiceName)
        .install();

    getWorkflowMessageHandler.setWorkflowRepositoryService(workflowRepositoryService);

    listWorkflowsControlMessageHandler.setWorkflowRepositoryService(workflowRepositoryService);
  }

  @Override
  public void onClose() {
    getWorkflowMessageHandler.setWorkflowRepositoryService(null);
    listWorkflowsControlMessageHandler.setWorkflowRepositoryService(null);
  }
}
