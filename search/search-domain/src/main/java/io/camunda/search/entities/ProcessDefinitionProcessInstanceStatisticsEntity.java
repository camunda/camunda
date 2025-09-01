package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.util.ObjectBuilder;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessDefinitionProcessInstanceStatisticsEntity(
    String processDefinitionId,
    String latestProcessDefinitionName,
    Boolean hasMultipleVersions,
    Long activeInstancesWithoutIncidentCount,
    Long activeInstancesWithIncidentCount) {

  public static class Builder
      implements ObjectBuilder<ProcessDefinitionProcessInstanceStatisticsEntity> {
    private String processDefinitionId;
    private String latestProcessDefinitionName;
    private Boolean hasMultipleVersions;
    private Long activeInstancesWithoutIncidentCount;
    private Long activeInstancesWithIncidentCount;

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder latestProcessDefinitionName(final String latestProcessDefinitionName) {
      this.latestProcessDefinitionName = latestProcessDefinitionName;
      return this;
    }

    public Builder hasMultipleVersions(final Boolean hasMultipleVersions) {
      this.hasMultipleVersions = hasMultipleVersions;
      return this;
    }

    public Builder activeInstancesWithoutIncidentCount(final Long count) {
      activeInstancesWithoutIncidentCount = count;
      return this;
    }

    public Builder activeInstancesWithIncidentCount(final Long count) {
      activeInstancesWithIncidentCount = count;
      return this;
    }

    @Override
    public ProcessDefinitionProcessInstanceStatisticsEntity build() {
      return new ProcessDefinitionProcessInstanceStatisticsEntity(
          processDefinitionId,
          latestProcessDefinitionName,
          hasMultipleVersions,
          activeInstancesWithoutIncidentCount,
          activeInstancesWithIncidentCount);
    }
  }
}
