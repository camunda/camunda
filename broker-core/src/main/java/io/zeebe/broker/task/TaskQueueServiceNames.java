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

import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.servicecontainer.ServiceName;

public class TaskQueueServiceNames
{
    public static final ServiceName<TaskQueueManager> TASK_QUEUE_MANAGER = ServiceName.newServiceName("taskqueue.manager", TaskQueueManager.class);
    public static final ServiceName<TaskSubscriptionManager> TASK_QUEUE_SUBSCRIPTION_MANAGER = ServiceName.newServiceName("taskqueue.subscription.manager", TaskSubscriptionManager.class);

    public static final ServiceName<StreamProcessorController> TASK_QUEUE_STREAM_PROCESSOR_SERVICE_GROUP_NAME = ServiceName.newServiceName("taskqueue.processor.instance", StreamProcessorController.class);

    public static ServiceName<StreamProcessorController> taskQueueInstanceStreamProcessorServiceName(String taskQueueName)
    {
        return ServiceName.newServiceName(String.format("taskqueue.%s.processor.instance", taskQueueName), StreamProcessorController.class);
    }

    public static ServiceName<StreamProcessorController> taskQueueLockStreamProcessorServiceName(String taskQueueName, String taskType)
    {
        return ServiceName.newServiceName(String.format("taskqueue.%s.processor.lock.%s", taskQueueName, taskType), StreamProcessorController.class);
    }

    public static ServiceName<StreamProcessorController> taskQueueExpireLockStreamProcessorServiceName(String taskQueueName)
    {
        return ServiceName.newServiceName(String.format("taskqueue.%s.processor.expire.lock", taskQueueName), StreamProcessorController.class);
    }
}
