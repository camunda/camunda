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
package io.camunda.client.api.search.response;

import io.camunda.client.api.search.enums.ProcessInstanceState;
import java.time.OffsetDateTime;
import java.util.Set;

public interface ProcessInstance {

  Long getProcessInstanceKey();

  String getProcessDefinitionId();

  String getProcessDefinitionName();

  Integer getProcessDefinitionVersion();

  String getProcessDefinitionVersionTag();

  Long getProcessDefinitionKey();

  Long getParentProcessInstanceKey();

  /**
   * Returns the key of the root process instance. The root process instance is the top-level
   * ancestor in the process instance hierarchy.
   *
   * <p><strong>Note:</strong> This field is {@code null} for process instance hierarchies created
   * before version 8.9.
   *
   * @return the root process instance key, or {@code null} for data created before version 8.9
   */
  Long getRootProcessInstanceKey();

  Long getParentElementInstanceKey();

  OffsetDateTime getStartDate();

  OffsetDateTime getEndDate();

  ProcessInstanceState getState();

  Boolean getHasIncident();

  String getTenantId();

  Set<String> getTags();
}
