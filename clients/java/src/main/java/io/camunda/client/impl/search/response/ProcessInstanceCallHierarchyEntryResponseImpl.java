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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.response.ProcessInstanceCallHierarchyEntryResponse;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.ProcessInstanceCallHierarchyEntry;

public class ProcessInstanceCallHierarchyEntryResponseImpl
    implements ProcessInstanceCallHierarchyEntryResponse {

  private final Long processInstanceKey;
  private final Long processDefinitionKey;
  private final String processDefinitionName;

  public ProcessInstanceCallHierarchyEntryResponseImpl(
      final ProcessInstanceCallHierarchyEntry entry) {
    processInstanceKey = ParseUtil.parseLongOrNull(entry.getProcessInstanceKey());
    processDefinitionKey = ParseUtil.parseLongOrNull(entry.getProcessDefinitionKey());
    processDefinitionName = entry.getProcessDefinitionName();
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getProcessDefinitionName() {
    return processDefinitionName;
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(processInstanceKey, processDefinitionKey, processDefinitionName);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final ProcessInstanceCallHierarchyEntryResponseImpl other =
        (ProcessInstanceCallHierarchyEntryResponseImpl) obj;
    return java.util.Objects.equals(processInstanceKey, other.processInstanceKey)
        && java.util.Objects.equals(processDefinitionKey, other.processDefinitionKey)
        && java.util.Objects.equals(processDefinitionName, other.processDefinitionName);
  }

  @Override
  public String toString() {
    return String.format(
        "ProcessInstanceCallHierarchyEntryImpl{processInstanceKey=%d, processDefinitionKey=%d, processDefinitionName='%s'}",
        processInstanceKey, processDefinitionKey, processDefinitionName);
  }
}
