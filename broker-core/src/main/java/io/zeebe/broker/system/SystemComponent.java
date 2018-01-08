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

import static io.zeebe.broker.system.SystemServiceNames.*;

import io.zeebe.broker.clustering.ClusterServiceNames;
import io.zeebe.broker.logstreams.LogStreamServiceNames;
import io.zeebe.broker.services.CountersManagerService;
import io.zeebe.broker.system.deployment.service.DeploymentManager;
import io.zeebe.broker.system.deployment.service.WorkflowRequestMessageHandlerService;
import io.zeebe.broker.system.executor.ScheduledExecutorService;
import io.zeebe.broker.system.log.PartitionManagerService;
import io.zeebe.broker.system.log.SystemPartitionManager;
import io.zeebe.broker.system.threads.ActorSchedulerService;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.ServiceContainer;

public class SystemComponent implements Component
{

    @Override
    public void init(SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();

        final CountersManagerService countersManagerService = new CountersManagerService(context.getConfigurationManager());
        serviceContainer.createService(COUNTERS_MANAGER_SERVICE, countersManagerService)
            .install();

        final ActorSchedulerService agentRunnerService = new ActorSchedulerService(context.getDiagnosticContext(), context.getConfigurationManager());
        serviceContainer.createService(ACTOR_SCHEDULER_SERVICE, agentRunnerService)
            .install();

        final ScheduledExecutorService executorService = new ScheduledExecutorService();
        serviceContainer.createService(EXECUTOR_SERVICE, executorService)
            .dependency(ACTOR_SCHEDULER_SERVICE, executorService.getActorSchedulerInjector())
            .install();

        final SystemConfiguration systemConfiguration = context.getConfigurationManager().readEntry("system", SystemConfiguration.class);

        final PartitionManagerService partitionManagerService = new PartitionManagerService();
        serviceContainer.createService(SystemServiceNames.PARTITION_MANAGER_SERVICE, partitionManagerService)
            .dependency(ClusterServiceNames.MEMBER_LIST_SERVICE, partitionManagerService.getMemberListServiceInjector())
            .dependency(TransportServiceNames.clientTransport(TransportServiceNames.MANAGEMENT_API_CLIENT_NAME), partitionManagerService.getManagementClientInjector())
            .install();

        final SystemPartitionManager systemPartitionManager = new SystemPartitionManager(systemConfiguration);
        serviceContainer.createService(SystemServiceNames.SYSTEM_LOG_MANAGER, systemPartitionManager)
            .dependency(TransportServiceNames.serverTransport(TransportServiceNames.CLIENT_API_SERVER_NAME), systemPartitionManager.getClientApiTransportInjector())
            .dependency(EXECUTOR_SERVICE, systemPartitionManager.getExecutorInjector())
            .dependency(PARTITION_MANAGER_SERVICE, systemPartitionManager.getPartitionManagerInjector())
            .groupReference(LogStreamServiceNames.SYSTEM_STREAM_GROUP, systemPartitionManager.getLogStreamsGroupReference())
            .install();

        final DeploymentManager deploymentManagerService = new DeploymentManager(systemConfiguration);
        serviceContainer.createService(SystemServiceNames.DEPLOYMENT_MANAGER_SERVICE, deploymentManagerService)
            .dependency(TransportServiceNames.clientTransport(TransportServiceNames.MANAGEMENT_API_CLIENT_NAME), deploymentManagerService.getManagementClientInjector())
            .dependency(TransportServiceNames.serverTransport(TransportServiceNames.CLIENT_API_SERVER_NAME), deploymentManagerService.getClientApiTransportInjector())
            .dependency(PARTITION_MANAGER_SERVICE, deploymentManagerService.getPartitionManagerInjector())
            .dependency(EXECUTOR_SERVICE, deploymentManagerService.getScheduledExecutorInjector())
            .groupReference(LogStreamServiceNames.SYSTEM_STREAM_GROUP, deploymentManagerService.getSystemStreamGroupReference())
            .install();

        final WorkflowRequestMessageHandlerService workflowRequestHandlerService = new WorkflowRequestMessageHandlerService();
        serviceContainer.createService(WORKFLOW_REQUEST_MESSAGE_HANDLER_SERVICE, workflowRequestHandlerService)
            .groupReference(LogStreamServiceNames.WORKFLOW_STREAM_GROUP, workflowRequestHandlerService.getLogStreamsGroupReference())
            .install();


    }

}
