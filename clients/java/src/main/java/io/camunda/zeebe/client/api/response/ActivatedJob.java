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
package io.camunda.zeebe.client.api.response;

import io.camunda.zeebe.client.api.ExperimentalApi;
import io.camunda.zeebe.client.api.command.ClientException;
import java.util.Map;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.response.ActivatedJob}
 */
@Deprecated
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
   *     available variables, otherwise throw a {@link ClientException}
   */
  Object getVariable(String name);

  /**
   * @return the record encoded as JSON
   */
  String toJson();

  /**
   * @return the identifier of the tenant that owns the job
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/13560")
  String getTenantId();
}
