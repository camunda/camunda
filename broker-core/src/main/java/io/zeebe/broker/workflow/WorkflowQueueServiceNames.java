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
package io.zeebe.broker.workflow;

import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.servicecontainer.ServiceName;

public class WorkflowQueueServiceNames
{
    public static final ServiceName<WorkflowQueueManager> WORKFLOW_QUEUE_MANAGER = ServiceName.newServiceName("workflow.manager", WorkflowQueueManager.class);

    public static ServiceName<StreamProcessorController> deploymentStreamProcessorServiceName(String queueName)
    {
        return ServiceName.newServiceName(String.format("workflow.%s.processor.deployment", queueName), StreamProcessorController.class);
    }

    public static ServiceName<StreamProcessorController> workflowInstanceStreamProcessorServiceName(String queueName)
    {
        return ServiceName.newServiceName(String.format("workflow.%s.processor.instance", queueName), StreamProcessorController.class);
    }

    public static ServiceName<StreamProcessorController> incidentStreamProcessorServiceName(String queueName)
    {
        return ServiceName.newServiceName(String.format("workflow.%s.processor.incident", queueName), StreamProcessorController.class);
    }
}
