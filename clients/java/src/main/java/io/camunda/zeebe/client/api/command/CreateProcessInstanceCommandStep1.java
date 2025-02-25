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

import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.CreateProcessInstanceCommandStep1}
 */
@Deprecated
public interface CreateProcessInstanceCommandStep1
    extends CommandWithCommunicationApiStep<CreateProcessInstanceCommandStep1> {
  /** Use the latest version of the process (without guarantee). */
  int LATEST_VERSION = -1;

  /**
   * Set the BPMN process id of the process to create an instance of. This is the static id of the
   * process in the BPMN XML (i.e. "&#60;bpmn:process id='my-process'&#62;").
   *
   * @param bpmnProcessId the BPMN process id of the process
   * @return the builder for this command
   */
  CreateProcessInstanceCommandStep2 bpmnProcessId(String bpmnProcessId);

  /**
   * Set the key of the process to create an instance of. The key is assigned by the broker while
   * deploying the process. It can be picked from the deployment or process event.
   *
   * @param processDefinitionKey the key of the process
   * @return the builder for this command
   */
  CreateProcessInstanceCommandStep3 processDefinitionKey(long processDefinitionKey);

  interface CreateProcessInstanceCommandStep2 {
    /**
     * Set the version of the process to create an instance of. The version is assigned by the
     * broker while deploying the process. It can be picked from the deployment or process event.
     *
     * @param version the version of the process
     * @return the builder for this command
     */
    CreateProcessInstanceCommandStep3 version(int version);

    /**
     * Use the latest version of the process to create an instance of.
     *
     * <p>If the latest version was deployed few moments before then it can happen that the new
     * instance is created of the previous version.
     *
     * @return the builder for this command
     */
    CreateProcessInstanceCommandStep3 latestVersion();
  }

  interface CreateProcessInstanceCommandStep3
      extends CommandWithTenantStep<CreateProcessInstanceCommandStep3>,
          FinalCommandStep<ProcessInstanceEvent> {
    /**
     * Set the initial variables of the process instance.
     *
     * @param variables the variables JSON document as stream
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CreateProcessInstanceCommandStep3 variables(InputStream variables);

    /**
     * Set the initial variables of the process instance.
     *
     * @param variables the variables JSON document as String
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CreateProcessInstanceCommandStep3 variables(String variables);

    /**
     * Set the initial variables of the process instance.
     *
     * @param variables the variables document as map
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CreateProcessInstanceCommandStep3 variables(Map<String, Object> variables);

    /**
     * Set the initial variables of the process instance.
     *
     * @param variables the variables document as object to be serialized to JSON
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CreateProcessInstanceCommandStep3 variables(Object variables);

    /**
     * Set a single initial variable of the process instance.
     *
     * @param key the key of the variable as string
     * @param value the value of the variable as object
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CreateProcessInstanceCommandStep3 variable(String key, Object value);

    /**
     * Overrides the default start position of the process. Calling this method will make the
     * process start at the given {@code elementId}, if possible. This method can be called more
     * than once to simultaneously start at different elements in different branches of the process.
     *
     * @param elementId the id of the BPMN element where to start the process instance
     * @return the builder for this command. Call {@link #send()} to complete the command and send *
     *     it to the broker.
     */
    CreateProcessInstanceCommandStep3 startBeforeElement(final String elementId);

    /**
     * When this method is called, the response to the command will be received after the process is
     * completed. The response consists of a set of variables.
     *
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker
     */
    CreateProcessInstanceWithResultCommandStep1 withResult();
  }

  interface CreateProcessInstanceWithResultCommandStep1
      extends CommandWithTenantStep<CreateProcessInstanceWithResultCommandStep1>,
          CommandWithCommunicationApiStep<CreateProcessInstanceWithResultCommandStep1>,
          FinalCommandStep<ProcessInstanceResult> {

    /**
     * Set a list of variables names which should be fetched in the response.
     *
     * @param fetchVariables set of names of variables to be included in the response
     * @return the builder for this command. Call {@link #send()} to complete the command and send *
     *     it to the broker
     */
    CreateProcessInstanceWithResultCommandStep1 fetchVariables(List<String> fetchVariables);

    /**
     * Set a list of variables names which should be fetched in the response.
     *
     * @param fetchVariables set of names of variables to be included in the response
     * @return the builder for this command. Call {@link #send()} to complete the command and send *
     *     it to the broker
     */
    CreateProcessInstanceWithResultCommandStep1 fetchVariables(String... fetchVariables);
  }
}
