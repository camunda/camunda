package io.camunda.client.impl.statistics.sort;

import io.camunda.client.api.statistics.sort.ProcessDefinitionInstanceStatisticsSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class ProcessDefinitionInstanceStatisticsSortImpl
    extends SearchRequestSortBase<ProcessDefinitionInstanceStatisticsSort>
    implements ProcessDefinitionInstanceStatisticsSort {

  @Override
  public ProcessDefinitionInstanceStatisticsSort processDefinitionId() {
    return field("processDefinitionId");
  }

  @Override
  public ProcessDefinitionInstanceStatisticsSort activeInstancesWithIncidentCount() {
    return field("activeInstancesWithIncidentCount");
  }

  @Override
  public ProcessDefinitionInstanceStatisticsSort activeInstancesWithoutIncidentCount() {
    return field("activeInstancesWithoutIncidentCount");
  }

  @Override
  protected ProcessDefinitionInstanceStatisticsSort self() {
    return this;
  }
}
