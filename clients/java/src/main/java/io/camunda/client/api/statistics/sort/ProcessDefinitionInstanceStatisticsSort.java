package io.camunda.client.api.statistics.sort;

import io.camunda.client.api.search.request.TypedSortableRequest.SearchRequestSort;

public interface ProcessDefinitionInstanceStatisticsSort
    extends SearchRequestSort<ProcessDefinitionInstanceStatisticsSort> {

  ProcessDefinitionInstanceStatisticsSort processDefinitionId();

  ProcessDefinitionInstanceStatisticsSort activeInstancesWithIncidentCount();

  ProcessDefinitionInstanceStatisticsSort activeInstancesWithoutIncidentCount();
}
