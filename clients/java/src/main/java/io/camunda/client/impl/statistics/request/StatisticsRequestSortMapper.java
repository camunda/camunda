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
package io.camunda.client.impl.statistics.request;

import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByDefinitionQuerySortRequest;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByErrorQuerySortRequest;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceStatisticsQuerySortRequest;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsQuerySortRequest;
import java.util.List;
import java.util.stream.Collectors;

public class StatisticsRequestSortMapper {

  public static List<ProcessDefinitionInstanceVersionStatisticsQuerySortRequest>
      toProcessDefinitionInstanceVersionStatisticsSortRequests(
          final List<SearchRequestSort> requests) {
    return requests.stream()
        .map(
            r -> {
              final ProcessDefinitionInstanceVersionStatisticsQuerySortRequest request =
                  new ProcessDefinitionInstanceVersionStatisticsQuerySortRequest();
              request.setField(
                  ProcessDefinitionInstanceVersionStatisticsQuerySortRequest.FieldEnum.fromValue(
                      r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<ProcessDefinitionInstanceStatisticsQuerySortRequest>
      toProcessDefinitionInstanceStatisticsSortRequests(final List<SearchRequestSort> requests) {
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

  public static List<IncidentProcessInstanceStatisticsByErrorQuerySortRequest>
      toIncidentProcessInstanceStatisticsByErrorSortRequests(
          final List<SearchRequestSort> requests) {
    return requests.stream()
        .map(
            r -> {
              final IncidentProcessInstanceStatisticsByErrorQuerySortRequest request =
                  new IncidentProcessInstanceStatisticsByErrorQuerySortRequest();
              request.setField(
                  IncidentProcessInstanceStatisticsByErrorQuerySortRequest.FieldEnum.fromValue(
                      r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }

  public static List<IncidentProcessInstanceStatisticsByDefinitionQuerySortRequest>
      toIncidentProcessInstanceStatisticsByDefinitionRequestSortRequests(
          final List<SearchRequestSort> requests) {
    return requests.stream()
        .map(
            r -> {
              final IncidentProcessInstanceStatisticsByDefinitionQuerySortRequest request =
                  new IncidentProcessInstanceStatisticsByDefinitionQuerySortRequest();
              request.setField(
                  IncidentProcessInstanceStatisticsByDefinitionQuerySortRequest.FieldEnum.fromValue(
                      r.getField()));
              request.setOrder(r.getOrder());
              return request;
            })
        .collect(Collectors.toList());
  }
}
