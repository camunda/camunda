/**
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.workflow.cmd;

/**
 * Represents a workflow instance, which has been created.
 */
public interface WorkflowInstance
{
    /**
     * The BPMN process id which identifies the workflow definition.
     * @return the id of the BPMN process
     */
    String getBpmnProcessId();

    /**
     * The workflow instance key which identifies the workflow instance.
     * @return the workflow instance key
     */
    long getWorkflowInstanceKey();

    /**
     * The version of the workflow definition, which is used to created this instance,
     * @return the version of the workflow definition
     */
    int getVersion();

    /**
     * The payload of the current running workflow instance.
     * @return the payload of the workflow instance
     */
    String getPayload();
}
