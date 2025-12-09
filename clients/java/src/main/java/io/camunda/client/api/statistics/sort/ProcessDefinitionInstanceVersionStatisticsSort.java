package io.camunda.client.api.statistics.sort;

import io.camunda.client.api.search.request.TypedSortableRequest.SearchRequestSort;

public interface ProcessDefinitionInstanceVersionStatisticsSort
    extends SearchRequestSort<ProcessDefinitionInstanceVersionStatisticsSort> {

  ProcessDefinitionInstanceVersionStatisticsSort processDefinitionId();

  ProcessDefinitionInstanceVersionStatisticsSort processDefinitionKey();

  ProcessDefinitionInstanceVersionStatisticsSort processDefinitionName();

  ProcessDefinitionInstanceVersionStatisticsSort processDefinitionVersion();

  ProcessDefinitionInstanceVersionStatisticsSort activeInstancesWithoutIncidentCount();

  ProcessDefinitionInstanceVersionStatisticsSort activeInstancesWithIncidentCount();
}
