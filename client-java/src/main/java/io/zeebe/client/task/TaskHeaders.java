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
package io.zeebe.client.task;

/**
 * A list of predefined task header keys.
 */
public interface TaskHeaders
{
    String WORKFLOW_INSTANCE_KEY = "workflowInstanceKey";

    String BPMN_PROCESS_ID = "bpmnProcessId";

    String WORKFLOW_DEFINITION_VERSION = "workflowDefinitionVersion";

    String ACTIVITY_ID = "activityId";

    String ACTIVITY_INSTANCE_KEY = "activityInstanceKey";

    String CUSTOM = "customHeaders";
    String CUSTOM_HEADER_KEY = "key";
    String CUSTOM_HEADER_VALUE = "value";

}
