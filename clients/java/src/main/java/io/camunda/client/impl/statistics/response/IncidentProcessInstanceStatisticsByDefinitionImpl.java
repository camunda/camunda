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
package io.camunda.client.impl.statistics.response;

import io.camunda.client.api.statistics.response.IncidentProcessInstanceStatisticsByDefinition;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByDefinitionResult;
import java.util.Objects;

public class IncidentProcessInstanceStatisticsByDefinitionImpl
    implements IncidentProcessInstanceStatisticsByDefinition {

  private final String processDefinitionId;
  private final Long processDefinitionKey;
  private final String processDefinitionName;
  private final Integer processDefinitionVersion;
  private final String tenantId;
  private final Long activeInstancesWithErrorCount;

  public IncidentProcessInstanceStatisticsByDefinitionImpl(
      final IncidentProcessInstanceStatisticsByDefinitionResult result) {
    processDefinitionId = result.getProcessDefinitionId();
    processDefinitionKey = ParseUtil.parseLongOrNull(result.getProcessDefinitionKey());
    processDefinitionName = result.getProcessDefinitionName();
    processDefinitionVersion = result.getProcessDefinitionVersion();
    tenantId = result.getTenantId();
    activeInstancesWithErrorCount = result.getActiveInstancesWithErrorCount();
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
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
  public Integer getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public Long getActiveInstancesWithErrorCount() {
    return activeInstancesWithErrorCount;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        processDefinitionId,
        processDefinitionKey,
        processDefinitionName,
        processDefinitionVersion,
        tenantId,
        activeInstancesWithErrorCount);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final IncidentProcessInstanceStatisticsByDefinitionImpl other =
        (IncidentProcessInstanceStatisticsByDefinitionImpl) obj;
    return Objects.equals(processDefinitionId, other.processDefinitionId)
        && Objects.equals(processDefinitionKey, other.processDefinitionKey)
        && Objects.equals(processDefinitionName, other.processDefinitionName)
        && Objects.equals(processDefinitionVersion, other.processDefinitionVersion)
        && Objects.equals(tenantId, other.tenantId)
        && Objects.equals(activeInstancesWithErrorCount, other.activeInstancesWithErrorCount);
  }

  @Override
  public String toString() {
    return String.format(
        "IncidentProcessInstanceStatisticsByDefinitionImpl{processDefinitionId=%s, processDefinitionKey=%s, processDefinitionName='%s', processDefinitionVersion=%s, tenantId='%s', activeInstancesWithErrorCount=%s}",
        processDefinitionId,
        processDefinitionKey,
        processDefinitionName,
        processDefinitionVersion,
        tenantId,
        activeInstancesWithErrorCount);
  }
}
