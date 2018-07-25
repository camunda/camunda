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
package io.zeebe.client.api.clients;

import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.events.WorkflowInstanceEvent;

/**
 * A client with access to all workflow-related operations:
 * <li>deploy a workflow
 * <li>create a workflow instance
 * <li>cancel a workflow instance
 * <li>update the payload of a workflow instance
 * <li>request a workflow resource
 * <li>request all deployed workflows
 */
public interface WorkflowClient {

  /**
   * Command to deploy new workflows.
   *
   * <pre>
   * workflowClient
   *  .newDeployCommand()
   *  .addResourceFile("~/wf/workflow1.bpmn")
   *  .addResourceFile("~/wf/workflow2.bpmn")
   *  .send();
   * </pre>
   *
   * @return a builder for the command
   */
  DeployWorkflowCommandStep1 newDeployCommand();

  /**
   * Command to create/start a new instance of a workflow.
   *
   * <pre>
   * workflowClient
   *  .newCreateInstanceCommand()
   *  .bpmnProcessId("my-process")
   *  .latestVersion()
   *  .payload(json)
   *  .send();
   * </pre>
   *
   * @return a builder for the command
   */
  CreateWorkflowInstanceCommandStep1 newCreateInstanceCommand();

  /**
   * Command to cancel a workflow instance.
   *
   * <pre>
   * workflowClient
   *  .newCancelInstanceCommand(workflowInstanceEvent)
   *  .send();
   * </pre>
   *
   * The workflow instance is specified by the given event. The event must be the latest event of
   * the workflow instance to ensure that the command is based on the latest state of the workflow
   * instance. If it's not the latest one then the command is rejected.
   *
   * @param event the latest workflow instance event
   * @return a builder for the command
   */
  CancelWorkflowInstanceCommandStep1 newCancelInstanceCommand(WorkflowInstanceEvent event);

  /**
   * Command to update the payload of a workflow instance.
   *
   * <pre>
   * workflowClient
   *  .newUpdatePayloadCommand(workflowInstanceEvent)
   *  .payload(json)
   *  .send();
   * </pre>
   *
   * The workflow instance is specified by the given event. The event must be the latest event of
   * the workflow instance to ensure that the command is based on the latest state of the workflow
   * instance. If it's not the latest one then the command is rejected.
   *
   * <p>If the workflow instance failed because of a payload-related incident then it will try to
   * resolve the incident with the given payload.
   *
   * @param event the latest workflow instance event
   * @return a builder for the command
   */
  UpdatePayloadWorkflowInstanceCommandStep1 newUpdatePayloadCommand(WorkflowInstanceEvent event);

  /**
   * Request to get the resource of a workflow (i.e. the XML representation).
   *
   * <pre>
   * WorkflowResource resource = workflowClient
   *  .newResourceRequest()
   *  .bpmnProcessId("my-process")
   *  .lastestVersion()
   *  .send()
   *  .join();
   *
   * String bpmnXml = resoure.getBpmnXml();
   * </pre>
   *
   * @return a builder of the request
   */
  WorkflowResourceRequestStep1 newResourceRequest();

  /**
   * Request to get all deployed workflows.
   *
   * <pre>
   * List&#60;Workflow&#62; workflows = workflowClient
   *  .newWorkflowRequest()
   *  .send()
   *  .join()
   *  .getWorkflows();
   *
   * String bpmnProcessId = workflow.getBpmnProcessId();
   * </pre>
   *
   * The response does not contain the resources of the workflows. Use {@link #newResourceRequest()}
   * to get the resource of a workflow.
   *
   * @see #newResourceRequest()
   * @return a builder of the request
   */
  WorkflowRequestStep1 newWorkflowRequest();
}
