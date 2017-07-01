/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
