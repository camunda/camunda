package io.camunda.client.impl.statistics.request;

import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceStatisticsQuerySortRequest;
import java.util.List;
import java.util.stream.Collectors;

public class StatisticsRequestSortMapper {

  public static List<ProcessDefinitionInstanceStatisticsQuerySortRequest>
      toProcessDefinitionInstanceVersionStatisticsSortRequests(
          final List<SearchRequestSort> requests) {
    return requests.stream()
        .map(
            r -> {
              final ProcessDefinitionInstanceStatisticsQuerySortRequest request =
                  new ProcessDefinitionInstanceStatisticsQuerySortRequest();
              request.setField(
                  ProcessDefinitionInstanceStatisticsQuerySortRequest.FieldEnum.fromValue(
                      r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }
}
