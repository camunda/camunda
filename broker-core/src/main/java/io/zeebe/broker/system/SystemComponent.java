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

import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.COUNTERS_MANAGER_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.EXECUTOR_SERVICE;

import io.zeebe.broker.logstreams.LogStreamServiceNames;
import io.zeebe.broker.services.CountersManagerService;
import io.zeebe.broker.system.executor.ScheduledExecutorService;
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

        final ActorSchedulerService agentRunnerService = new ActorSchedulerService(context.getConfigurationManager());
        serviceContainer.createService(ACTOR_SCHEDULER_SERVICE, agentRunnerService)
            .install();

        final ScheduledExecutorService executorService = new ScheduledExecutorService();
        serviceContainer.createService(EXECUTOR_SERVICE, executorService)
            .dependency(ACTOR_SCHEDULER_SERVICE, executorService.getActorSchedulerInjector())
            .install();

        final SystemPartitionManager systemPartitionManager = new SystemPartitionManager();
        serviceContainer.createService(SystemServiceNames.SYSTEM_LOG_MANAGER, systemPartitionManager)
            .dependency(TransportServiceNames.serverTransport(TransportServiceNames.CLIENT_API_SERVER_NAME), systemPartitionManager.getClientApiTransportInjector())
            .groupReference(LogStreamServiceNames.SYSTEM_STREAM_GROUP, systemPartitionManager.getLogStreamsGroupReference())
            .install();

    }

}
