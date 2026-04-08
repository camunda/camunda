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
package io.camunda.zeebe.model.bpmn.instance.zeebe;

import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;

public interface ZeebeTaskDefinition extends BpmnModelElementInstance {

  String DEFAULT_RETRIES = "3";

  String getType();

  void setType(String type);

  String getRetries();

  void setRetries(String retries);

  /**
   * Gets the priority expression or literal value for jobs created from this task definition.
   *
   * <p>Priority is used for priority-based job activation when workers request jobs with {@code
   * usePriority=true}. Higher values indicate higher priority. Jobs with higher priority are
   * activated before jobs with lower priority.
   *
   * @return the priority expression/literal, or {@code null} if not set (inherits from process or
   *     defaults to 0)
   */
  String getPriority();

  /**
   * Sets the priority expression or literal value for jobs created from this task definition.
   *
   * @param priority the priority expression (e.g., "50") or FEEL expression (e.g., "=orderValue >
   *     1000 ? 90 : 10")
   */
  void setPriority(String priority);
}
