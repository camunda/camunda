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

import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import java.io.InputStream;
import java.nio.charset.Charset;

public interface DeployWorkflowCommandStep1 {

  /**
   * Add the given resource to the deployment.
   *
   * @param resourceBytes the workflow resource as byte array
   * @param resourceName the name of the resource (e.g. "workflow.bpmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployWorkflowCommandBuilderStep2 addResourceBytes(byte[] resourceBytes, String resourceName);

  /**
   * Add the given resource to the deployment.
   *
   * @param resourceString the workflow resource as String
   * @param charset the charset of the String
   * @param resourceName the name of the resource (e.g. "workflow.bpmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployWorkflowCommandBuilderStep2 addResourceString(
      String resourceString, Charset charset, String resourceName);

  /**
   * Add the given resource to the deployment.
   *
   * @param resourceString the workflow resource as UTF-8-encoded String
   * @param resourceName the name of the resource (e.g. "workflow.bpmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployWorkflowCommandBuilderStep2 addResourceStringUtf8(
      String resourceString, String resourceName);

  /**
   * Add the given resource to the deployment.
   *
   * @param resourceStream the workflow resource as stream
   * @param resourceName the name of the resource (e.g. "workflow.bpmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployWorkflowCommandBuilderStep2 addResourceStream(
      InputStream resourceStream, String resourceName);

  /**
   * Add the given resource to the deployment.
   *
   * @param classpathResource the path of the workflow resource in the classpath (e.g.
   *     "wf/workflow.bpmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployWorkflowCommandBuilderStep2 addResourceFromClasspath(String classpathResource);

  /**
   * Add the given resource to the deployment.
   *
   * @param filename the absolute path of the workflow resource (e.g. "~/wf/workflow.bpmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployWorkflowCommandBuilderStep2 addResourceFile(String filename);

  /**
   * Add the given workflow to the deployment.
   *
   * @param workflowDefinition the workflow as model
   * @param resourceName the name of the resource (e.g. "workflow.bpmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployWorkflowCommandBuilderStep2 addWorkflowModel(
      WorkflowDefinition workflowDefinition, String resourceName);

  interface DeployWorkflowCommandBuilderStep2
      extends DeployWorkflowCommandStep1, FinalCommandStep<DeploymentEvent> {
    // the place for new optional parameters
  }
}
