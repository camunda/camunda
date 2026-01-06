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

import io.camunda.client.api.statistics.response.ProcessDefinitionInstanceStatistics;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceStatisticsResult;
import java.util.Objects;

public class ProcessDefinitionInstanceStatisticsImpl
    implements ProcessDefinitionInstanceStatistics {

  private final String processDefinitionId;
  private final String tenantId;
  private final String latestProcessDefinitionName;
  private final Boolean hasMultipleVersions;
  private final Long activeInstancesWithoutIncidentCount;
  private final Long activeInstancesWithIncidentCount;

  public ProcessDefinitionInstanceStatisticsImpl(
      final ProcessDefinitionInstanceStatisticsResult result) {
    processDefinitionId = result.getProcessDefinitionId();
    tenantId = result.getTenantId();
    latestProcessDefinitionName = result.getLatestProcessDefinitionName();
    hasMultipleVersions = result.getHasMultipleVersions();
    activeInstancesWithoutIncidentCount = result.getActiveInstancesWithoutIncidentCount();
    activeInstancesWithIncidentCount = result.getActiveInstancesWithIncidentCount();
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String getLatestProcessDefinitionName() {
    return latestProcessDefinitionName;
  }

  @Override
  public Boolean getHasMultipleVersions() {
    return hasMultipleVersions;
  }

  @Override
  public Long getActiveInstancesWithoutIncidentCount() {
    return activeInstancesWithoutIncidentCount;
  }

  @Override
  public Long getActiveInstancesWithIncidentCount() {
    return activeInstancesWithIncidentCount;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        processDefinitionId,
        tenantId,
        latestProcessDefinitionName,
        hasMultipleVersions,
        activeInstancesWithoutIncidentCount,
        activeInstancesWithIncidentCount);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessDefinitionInstanceStatisticsImpl that =
        (ProcessDefinitionInstanceStatisticsImpl) o;
    return Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(latestProcessDefinitionName, that.latestProcessDefinitionName)
        && Objects.equals(hasMultipleVersions, that.hasMultipleVersions)
        && Objects.equals(
            activeInstancesWithoutIncidentCount, that.activeInstancesWithoutIncidentCount)
        && Objects.equals(activeInstancesWithIncidentCount, that.activeInstancesWithIncidentCount);
  }

  @Override
  public String toString() {
    return String.format(
        "ProcessDefinitionInstanceStatisticsImpl{processDefinitionId='%s', tenantId='%s', latestProcessDefinitionName='%s', hasMultipleVersions=%s, activeInstancesWithoutIncidentCount=%d, activeInstancesWithIncidentCount=%d}",
        processDefinitionId,
        tenantId,
        latestProcessDefinitionName,
        hasMultipleVersions,
        activeInstancesWithoutIncidentCount,
        activeInstancesWithIncidentCount);
  }
}
