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
package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.servicecontainer.ServiceName;

public class TaskQueueServiceNames
{
    public static final ServiceName<TaskQueueManager> TASK_QUEUE_MANAGER = ServiceName.newServiceName("taskqueue.manager", TaskQueueManager.class);
    public static final ServiceName<StreamProcessorController> TASK_QUEUE_STREAM_PROCESSOR_SERVICE_GROUP_NAME = ServiceName.newServiceName("taskqueue.processor", StreamProcessorController.class);

    public static ServiceName<StreamProcessorController> taskQueueStreamProcessorServiceName(String taskQueueName)
    {
        return ServiceName.newServiceName(String.format("taskqueue.%s.processor", taskQueueName), StreamProcessorController.class);
    }
}
