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

import static io.zeebe.broker.logstreams.LogStreamServiceNames.WORKFLOW_STREAM_GROUP;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.EXECUTOR_SERVICE;
import static io.zeebe.broker.task.TaskQueueServiceNames.TASK_QUEUE_MANAGER;
import static io.zeebe.broker.task.TaskQueueServiceNames.TASK_QUEUE_SUBSCRIPTION_MANAGER;
import static io.zeebe.broker.transport.TransportServiceNames.CLIENT_API_SERVER_NAME;

import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.ServiceContainer;

public class TaskQueueComponent implements Component
{

    @Override
    public void init(SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();

        final TaskSubscriptionManagerService taskSubscriptionManagerService = new TaskSubscriptionManagerService();
        serviceContainer.createService(TASK_QUEUE_SUBSCRIPTION_MANAGER, taskSubscriptionManagerService)
            .dependency(ACTOR_SCHEDULER_SERVICE, taskSubscriptionManagerService.getActorSchedulerInjector())
            .dependency(TransportServiceNames.serverTransport(TransportServiceNames.CLIENT_API_SERVER_NAME), taskSubscriptionManagerService.getClientApiTransportInjector())
            .groupReference(WORKFLOW_STREAM_GROUP, taskSubscriptionManagerService.getLogStreamsGroupReference())
            .install();

        final TaskQueueManagerService taskQueueManagerService = new TaskQueueManagerService();
        serviceContainer.createService(TASK_QUEUE_MANAGER, taskQueueManagerService)
            .dependency(TransportServiceNames.serverTransport(CLIENT_API_SERVER_NAME), taskQueueManagerService.getClientApiTransportInjector())
            .dependency(EXECUTOR_SERVICE, taskQueueManagerService.getExecutorInjector())
            .dependency(TASK_QUEUE_SUBSCRIPTION_MANAGER, taskQueueManagerService.getTaskSubscriptionManagerInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, taskQueueManagerService.getActorSchedulerInjector())
            .groupReference(WORKFLOW_STREAM_GROUP, taskQueueManagerService.getLogStreamsGroupReference())
            .install();

    }

}
