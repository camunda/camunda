package io.camunda.client.api.statistics.response;

public interface ProcessDefinitionInstanceStatistics {

  String getProcessDefinitionId();

  String getTenantId();

  String getLatestProcessDefinitionName();

  Boolean getHasMultipleVersions();

  Long getActiveInstancesWithoutIncidentCount();

  Long getActiveInstancesWithIncidentCount();
}
