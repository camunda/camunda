/*
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
package io.zeebe.client.api.commands;

public interface WorkflowResourceRequestStep1 {
  /**
   * Set the BPMN process id of the workflow to get the resource of. This is the static id of the
   * process in the BPMN XML (i.e. "&#60;bpmn:process id='my-workflow'&#62;").
   *
   * @param bpmnProcessId the BPMN process id of the workflow
   * @return the builder for this request
   */
  WorkflowResourceRequestStep2 bpmnProcessId(String bpmnProcessId);

  /**
   * Set the key of the workflow to get the resource of. The key is assigned by the broker while
   * deploying the workflow. It can be picked from the deployment or workflow event.
   *
   * @param workflowKey the key of the workflow
   * @return the builder for this request
   */
  WorkflowResourceRequestStep3 workflowKey(long workflowKey);

  interface WorkflowResourceRequestStep2 {
    /**
     * Set the version of the workflow to get the resource of. The version is assigned by the broker
     * while deploying the workflow. It can be picked from the deployment or workflow event.
     *
     * @param version the version of the workflow
     * @return the builder for this request
     */
    WorkflowResourceRequestStep3 version(int version);

    /**
     * Use the latest version of the workflow to get the resource of.
     *
     * @return the builder for this request
     */
    WorkflowResourceRequestStep3 latestVersion();
  }

  interface WorkflowResourceRequestStep3 extends FinalCommandStep<WorkflowResource> {
    // the place for new optional parameters
  }
}
