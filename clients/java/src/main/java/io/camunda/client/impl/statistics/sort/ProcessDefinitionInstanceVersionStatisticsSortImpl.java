package io.camunda.client.impl.statistics.sort;

import io.camunda.client.api.statistics.sort.ProcessDefinitionInstanceVersionStatisticsSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class ProcessDefinitionInstanceVersionStatisticsSortImpl
    extends SearchRequestSortBase<ProcessDefinitionInstanceVersionStatisticsSort>
    implements ProcessDefinitionInstanceVersionStatisticsSort {

  @Override
  public ProcessDefinitionInstanceVersionStatisticsSort processDefinitionId() {
    return field("processDefinitionId");
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsSort processDefinitionKey() {
    return field("processDefinitionKey");
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsSort processDefinitionName() {
    return field("processDefinitionName");
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsSort processDefinitionVersion() {
    return field("processDefinitionVersion");
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsSort activeInstancesWithoutIncidentCount() {
    return field("activeInstancesWithoutIncidentCount");
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsSort activeInstancesWithIncidentCount() {
    return field("activeInstancesWithIncidentCount");
  }

  @Override
  protected ProcessDefinitionInstanceVersionStatisticsSort self() {
    return this;
  }
}
