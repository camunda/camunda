package io.camunda.client.api.statistics.response;

public interface ProcessDefinitionInstanceVersionStatistics {

  String getProcessDefinitionId();

  Long getProcessDefinitionKey();

  String getProcessDefinitionName();

  String getTenantId();

  Integer getProcessDefinitionVersion();

  Long getActiveInstancesWithoutIncidentCount();

  Long getActiveInstancesWithIncidentCount();
}
