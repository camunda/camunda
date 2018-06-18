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
package io.zeebe.broker.workflow;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.*;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.STREAM_PROCESSOR_SERVICE_FACTORY;
import static io.zeebe.broker.transport.TransportServiceNames.*;
import static io.zeebe.broker.workflow.WorkflowServiceNames.WORKFLOW_QUEUE_MANAGER;

import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.servicecontainer.ServiceContainer;

public class WorkflowComponent implements Component {
  @Override
  public void init(SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();

    final WorkflowStreamProcessingManagerService workflowQueueManagerService =
        new WorkflowStreamProcessingManagerService();
    serviceContainer
        .createService(WORKFLOW_QUEUE_MANAGER, workflowQueueManagerService)
        .dependency(
            serverTransport(CLIENT_API_SERVER_NAME),
            workflowQueueManagerService.getClientApiTransportInjector())
        .dependency(
            TOPOLOGY_MANAGER_SERVICE, workflowQueueManagerService.getTopologyManagerInjector())
        .dependency(
            clientTransport(MANAGEMENT_API_CLIENT_NAME),
            workflowQueueManagerService.getManagementApiClientInjector())
        .dependency(
            STREAM_PROCESSOR_SERVICE_FACTORY,
            workflowQueueManagerService.getStreamProcessorServiceFactoryInjector())
        .groupReference(
            LEADER_PARTITION_GROUP_NAME, workflowQueueManagerService.getPartitionsGroupReference())
        .install();
  }
}
