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
package io.zeebe.broker.task;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.STREAM_PROCESSOR_SERVICE_FACTORY;
import static io.zeebe.broker.task.TaskQueueServiceNames.TASK_QUEUE_MANAGER;
import static io.zeebe.broker.task.TaskQueueServiceNames.TASK_QUEUE_SUBSCRIPTION_MANAGER;
import static io.zeebe.broker.transport.TransportServiceNames.CLIENT_API_SERVER_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.serverTransport;

import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.servicecontainer.ServiceContainer;

public class TaskQueueComponent implements Component
{
    @Override
    public void init(SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();

        final TaskSubscriptionManagerService taskSubscriptionManagerService = new TaskSubscriptionManagerService(serviceContainer);
        serviceContainer.createService(TASK_QUEUE_SUBSCRIPTION_MANAGER, taskSubscriptionManagerService)
            .dependency(serverTransport(CLIENT_API_SERVER_NAME), taskSubscriptionManagerService.getClientApiTransportInjector())
            .dependency(STREAM_PROCESSOR_SERVICE_FACTORY, taskSubscriptionManagerService.getStreamProcessorServiceFactoryInjector())
            .groupReference(LEADER_PARTITION_GROUP_NAME, taskSubscriptionManagerService.getLeaderPartitionsGroupReference())
            .install();

        final TaskQueueManagerService taskQueueManagerService = new TaskQueueManagerService();
        serviceContainer.createService(TASK_QUEUE_MANAGER, taskQueueManagerService)
            .dependency(serverTransport(CLIENT_API_SERVER_NAME), taskQueueManagerService.getClientApiTransportInjector())
            .dependency(TASK_QUEUE_SUBSCRIPTION_MANAGER, taskQueueManagerService.getTaskSubscriptionManagerInjector())
            .dependency(STREAM_PROCESSOR_SERVICE_FACTORY, taskQueueManagerService.getStreamProcessorServiceFactoryInjector())
            .groupReference(LEADER_PARTITION_GROUP_NAME, taskQueueManagerService.getPartitionsGroupReference())
            .install();
    }
}
