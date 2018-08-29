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

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.TOPOLOGY_MANAGER_SERVICE;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.STREAM_PROCESSOR_SERVICE_FACTORY;
import static io.zeebe.broker.transport.TransportServiceNames.CLIENT_API_SERVER_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.MANAGEMENT_API_CLIENT_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.SUBSCRIPTION_API_CLIENT_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.clientTransport;
import static io.zeebe.broker.transport.TransportServiceNames.serverTransport;
import static io.zeebe.broker.workflow.WorkflowServiceNames.WORKFLOW_MANAGER;

import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.ServiceContainer;

public class WorkflowComponent implements Component {
  @Override
  public void init(final SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();

    final WorkflowManagerService workflowManagerService = new WorkflowManagerService();
    serviceContainer
        .createService(WORKFLOW_MANAGER, workflowManagerService)
        .dependency(
            serverTransport(CLIENT_API_SERVER_NAME),
            workflowManagerService.getClientApiTransportInjector())
        .dependency(
            TransportServiceNames.CONTROL_MESSAGE_HANDLER_MANAGER,
            workflowManagerService.getControlMessageHandlerManagerServiceInjector())
        .dependency(TOPOLOGY_MANAGER_SERVICE, workflowManagerService.getTopologyManagerInjector())
        .dependency(
            clientTransport(MANAGEMENT_API_CLIENT_NAME),
            workflowManagerService.getManagementApiClientInjector())
        .dependency(
            clientTransport(SUBSCRIPTION_API_CLIENT_NAME),
            workflowManagerService.getSubscriptionApiClientInjector())
        .dependency(
            STREAM_PROCESSOR_SERVICE_FACTORY,
            workflowManagerService.getStreamProcessorServiceFactoryInjector())
        .groupReference(
            LEADER_PARTITION_GROUP_NAME, workflowManagerService.getPartitionsGroupReference())
        .install();
  }
}
