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
package io.camunda.zeebe.client.api.command;

import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.DeployResourceCommandStep1}
 */
@Deprecated
public interface DeployResourceCommandStep1
    extends CommandWithCommunicationApiStep<DeployResourceCommandStep1> {

  /**
   * Add the given resource to the deployment.
   *
   * @param resourceBytes the resource content as byte array
   * @param resourceName the name of the resource (e.g. "process.bpmn" or "decision.dmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployResourceCommandStep2 addResourceBytes(byte[] resourceBytes, String resourceName);

  /**
   * Add the given resource to the deployment.
   *
   * @param resourceString the resource content as String
   * @param charset the charset of the String
   * @param resourceName the name of the resource (e.g. "process.bpmn" or "decision.dmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployResourceCommandStep2 addResourceString(
      String resourceString, Charset charset, String resourceName);

  /**
   * Add the given resource to the deployment.
   *
   * @param resourceString the resource content as UTF-8-encoded String
   * @param resourceName the name of the resource (e.g. "process.bpmn" or "decision.dmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployResourceCommandStep2 addResourceStringUtf8(String resourceString, String resourceName);

  /**
   * Add the given resource to the deployment.
   *
   * @param resourceStream the resource content as stream
   * @param resourceName the name of the resource (e.g. "process.bpmn" or "decision.dmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployResourceCommandStep2 addResourceStream(InputStream resourceStream, String resourceName);

  /**
   * Add the given resource to the deployment.
   *
   * @param classpathResource the path of the resource file in the classpath (e.g. "wf/process.bpmn"
   *     or "dmn/decision.dmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployResourceCommandStep2 addResourceFromClasspath(String classpathResource);

  /**
   * Add the given resource to the deployment.
   *
   * @param filename the absolute path of the resource file (e.g. "~/wf/process.bpmn" or
   *     "~/dmn/decision.dmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployResourceCommandStep2 addResourceFile(String filename);

  /**
   * Add the given process as resource to the deployment.
   *
   * @param processDefinition the process as model
   * @param resourceName the name of the resource (e.g. "process.bpmn")
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  DeployResourceCommandStep2 addProcessModel(
      BpmnModelInstance processDefinition, String resourceName);

  /**
   * @deprecated since 8.8 for removal in 8.9, replaced by {@link
   *     io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2}
   */
  @Deprecated
  interface DeployResourceCommandStep2
      extends DeployResourceCommandStep1,
          CommandWithTenantStep<DeployResourceCommandStep2>,
          FinalCommandStep<DeploymentEvent> {
    // the place for new optional parameters
  }
}
