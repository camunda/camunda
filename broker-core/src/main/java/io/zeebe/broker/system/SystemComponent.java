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
package io.zeebe.broker.system;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.*;
import static io.zeebe.broker.system.SystemServiceNames.*;
import static io.zeebe.broker.transport.TransportServiceNames.*;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.*;

import io.zeebe.broker.system.deployment.service.DeploymentManager;
import io.zeebe.broker.system.deployment.service.WorkflowRequestMessageHandlerService;
import io.zeebe.broker.system.log.SystemPartitionManager;
import io.zeebe.broker.system.metrics.MetricsFileWriterService;
import io.zeebe.broker.system.metrics.cfg.MetricsCfg;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.ClientTransport;

public class SystemComponent implements Component
{
    @Override
    public void init(SystemContext context)
    {
        final ServiceName<ClientTransport> clientTransportServiceName = clientTransport(MANAGEMENT_API_CLIENT_NAME);

        final ServiceContainer serviceContainer = context.getServiceContainer();

        final MetricsFileWriterService metricsFileWriterService = new MetricsFileWriterService(context.getConfigurationManager().readEntry("metrics", MetricsCfg.class));
        serviceContainer.createService(METRICS_FILE_WRITER, metricsFileWriterService)
            .install();

        final SystemConfiguration systemConfiguration = context.getConfigurationManager().readEntry("system", SystemConfiguration.class);

        final SystemPartitionManager systemPartitionManager = new SystemPartitionManager(systemConfiguration);
        serviceContainer.createService(SYSTEM_LOG_MANAGER, systemPartitionManager)
            .dependency(serverTransport(CLIENT_API_SERVER_NAME), systemPartitionManager.getClientApiTransportInjector())
            .dependency(STREAM_PROCESSOR_SERVICE_FACTORY, systemPartitionManager.getStreamProcessorServiceFactoryInjector())
            .dependency(TOPOLOGY_MANAGER_SERVICE, systemPartitionManager.getTopologyManagerInjector())
            .dependency(clientTransportServiceName, systemPartitionManager.getClientTransportInjector())
            .groupReference(LEADER_PARTITION_SYSTEM_GROUP_NAME, systemPartitionManager.getPartitionsGroupReference())
            .install();

        final DeploymentManager deploymentManagerService = new DeploymentManager(systemConfiguration);
        serviceContainer.createService(DEPLOYMENT_MANAGER_SERVICE, deploymentManagerService)
            .dependency(clientTransportServiceName, deploymentManagerService.getManagementClientInjector())
            .dependency(serverTransport(CLIENT_API_SERVER_NAME), deploymentManagerService.getClientApiTransportInjector())
            .dependency(STREAM_PROCESSOR_SERVICE_FACTORY, deploymentManagerService.getStreamProcessorServiceFactoryInjector())
            .dependency(TOPOLOGY_MANAGER_SERVICE, deploymentManagerService.getTopologyManagerInjector())
            .groupReference(LEADER_PARTITION_SYSTEM_GROUP_NAME, deploymentManagerService.getPartitionsGroupReference())
            .install();

        final WorkflowRequestMessageHandlerService workflowRequestHandlerService = new WorkflowRequestMessageHandlerService();
        serviceContainer.createService(WORKFLOW_REQUEST_MESSAGE_HANDLER_SERVICE, workflowRequestHandlerService)
            .groupReference(LEADER_PARTITION_GROUP_NAME, workflowRequestHandlerService.getPartitionGroupReference())
            .install();
    }
}
