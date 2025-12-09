package io.camunda.client.impl.statistics.response;

import io.camunda.client.api.statistics.response.ProcessDefinitionInstanceVersionStatistics;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsResult;
import java.util.Objects;

public class ProcessDefinitionInstanceVersionStatisticsImpl
    implements ProcessDefinitionInstanceVersionStatistics {

  private final String processDefinitionId;
  private final Long processDefinitionKey;
  private final String processDefinitionName;
  private final String tenantId;
  private final Integer processDefinitionVersion;
  private final Long activeInstancesWithoutIncidentCount;
  private final Long activeInstancesWithIncidentCount;

  public ProcessDefinitionInstanceVersionStatisticsImpl(
      final ProcessDefinitionInstanceVersionStatisticsResult result) {
    processDefinitionId = result.getProcessDefinitionId();
    processDefinitionKey = ParseUtil.parseLongOrNull(result.getProcessDefinitionKey());
    processDefinitionName = result.getProcessDefinitionName();
    tenantId = result.getTenantId();
    processDefinitionVersion = result.getProcessDefinitionVersion();
    activeInstancesWithoutIncidentCount = result.getActiveInstancesWithoutIncidentCount();
    activeInstancesWithIncidentCount = result.getActiveInstancesWithIncidentCount();
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
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public Integer getProcessDefinitionVersion() {
    return processDefinitionVersion;
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
        processDefinitionKey,
        processDefinitionName,
        tenantId,
        processDefinitionVersion,
        activeInstancesWithoutIncidentCount,
        activeInstancesWithIncidentCount);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final ProcessDefinitionInstanceVersionStatisticsImpl other =
        (ProcessDefinitionInstanceVersionStatisticsImpl) obj;
    return Objects.equals(processDefinitionId, other.processDefinitionId)
        && Objects.equals(processDefinitionKey, other.processDefinitionKey)
        && Objects.equals(processDefinitionName, other.processDefinitionName)
        && Objects.equals(tenantId, other.tenantId)
        && Objects.equals(processDefinitionVersion, other.processDefinitionVersion)
        && Objects.equals(
            activeInstancesWithoutIncidentCount, other.activeInstancesWithoutIncidentCount)
        && Objects.equals(activeInstancesWithIncidentCount, other.activeInstancesWithIncidentCount);
  }

  @Override
  public String toString() {
    return String.format(
        "ProcessDefinitionInstanceVersionStatisticsImpl{"
            + "processDefinitionId='%s', "
            + "processDefinitionKey=%d, "
            + "processDefinitionName='%s', "
            + "tenantId='%s', "
            + "processDefinitionVersion=%d, "
            + "activeInstancesWithoutIncidentCount=%d, "
            + "activeInstancesWithIncidentCount=%d}",
        processDefinitionId,
        processDefinitionKey,
        processDefinitionName,
        tenantId,
        processDefinitionVersion,
        activeInstancesWithoutIncidentCount,
        activeInstancesWithIncidentCount);
  }
}
