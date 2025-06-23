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
package io.camunda.client.api.response;

import io.camunda.client.api.ExperimentalApi;
import io.camunda.client.api.command.ClientException;
import java.util.List;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.ListenerEventType;
import java.util.Map;

public interface ActivatedJob {

  /**
   * @return the unique key of the job
   */
  long getKey();

  /**
   * @return the type of the job
   */
  String getType();

  /**
   * @return key of the process instance
   */
  long getProcessInstanceKey();

  /**
   * @return BPMN process id of the process
   */
  String getBpmnProcessId();

  /**
   * @return version of the process
   */
  int getProcessDefinitionVersion();

  /**
   * @return key of the process
   */
  long getProcessDefinitionKey();

  /**
   * @return id of the process element
   */
  String getElementId();

  /**
   * @return key of the element instance
   */
  long getElementInstanceKey();

  /**
   * @return user-defined headers associated with this job
   */
  Map<String, String> getCustomHeaders();

  /**
   * @return the assigned worker to complete the job
   */
  String getWorker();

  /**
   * @return remaining retries
   */
  int getRetries();

  /**
   * @return the unix timestamp until when the job is exclusively assigned to this worker (time unit
   *     * is milliseconds since unix epoch). If the deadline is exceeded, it can happen that the
   *     job is handed to another worker and the work is performed twice.
   */
  long getDeadline();

  /**
   * @return JSON-formatted variables
   */
  String getVariables();

  /**
   * @return de-serialized variables as map
   */
  Map<String, Object> getVariablesAsMap();

  /**
   * @return de-serialized variables as the given type
   */
  <T> T getVariablesAsType(Class<T> variableType);

  /**
   * @return de-serialized variable value or null if the provided variable name is present among the
   *     available variables
   * @throws ClientException if the variable is missing
   */
  Object getVariable(String name);

  /**
   * @return user task properties associated with this job. Present only if the job is of kind
   *     {@code TASK_LISTENER}; returns {@code null} for other job kinds such as {@code
   *     BPMN_ELEMENT} or {@code EXECUTION_LISTENER}.
   */
  UserTaskProperties getUserTask();

  /**
   * @return the kind of the job.
   */
  JobKind getKind();

  /**
   * @return the listener event type of the job.
   */
  ListenerEventType getListenerEventType();

  /**
   * @return the record encoded as JSON
   */
  String toJson();

  /**
   * @return the identifier of the tenant that owns the job
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/13560")
  String getTenantId();

  /**
   * @return de-serialized document references if the provided variable name is present among the
   *     available variables and can be parsed as document reference
   * @throws ClientException if the variable is missing or if the variable cannot be parsed * as
   *     document reference list
   */
  List<DocumentReferenceResponse> getDocumentReferences(String name);
}
