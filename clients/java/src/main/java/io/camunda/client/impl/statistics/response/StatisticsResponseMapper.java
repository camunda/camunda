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
package io.camunda.client.impl.statistics.response;

import static java.util.Optional.ofNullable;

import io.camunda.client.api.statistics.response.ProcessElementStatistics;
import io.camunda.client.api.statistics.response.UsageMetricsStatistics;
import io.camunda.client.api.statistics.response.UsageMetricsStatisticsItem;
import io.camunda.client.protocol.rest.ProcessDefinitionElementStatisticsQueryResult;
import io.camunda.client.protocol.rest.UsageMetricsResponse;
import io.camunda.client.protocol.rest.UsageMetricsResponseItem;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class StatisticsResponseMapper {

  public static List<ProcessElementStatistics> toProcessDefinitionStatisticsResponse(
      final ProcessDefinitionElementStatisticsQueryResult response) {
    if (response.getItems() != null) {
      return response.getItems().stream()
          .map(ProcessElementStatisticsImpl::new)
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  public static UsageMetricsStatistics toUsageMetricsResponse(final UsageMetricsResponse response) {

    Map<String, UsageMetricsStatisticsItem> tenants = null;
    if (response.getTenants() != null) {
      tenants =
          response.getTenants().entrySet().stream()
              .collect(
                  Collectors.toMap(Entry::getKey, e -> toUsageMetricsResponseItem(e.getValue())));
    }

    return new UsageMetricsStatisticsImpl(
        ofNullable(response.getProcessInstances()).orElse(0L),
        ofNullable(response.getDecisionInstances()).orElse(0L),
        ofNullable(response.getAssignees()).orElse(0L),
        ofNullable(response.getActiveTenants()).orElse(0L),
        tenants);
  }

  public static UsageMetricsStatisticsItem toUsageMetricsResponseItem(
      final UsageMetricsResponseItem response) {
    return new UsageMetricsStatisticsItemImpl(
        ofNullable(response.getProcessInstances()).orElse(0L),
        ofNullable(response.getDecisionInstances()).orElse(0L),
        ofNullable(response.getAssignees()).orElse(0L));
  }
}
