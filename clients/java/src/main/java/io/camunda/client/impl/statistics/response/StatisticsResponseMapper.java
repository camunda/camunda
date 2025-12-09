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

import static io.camunda.client.impl.search.response.SearchResponseMapper.toSearchResponsePage;
import static java.util.Optional.ofNullable;

import io.camunda.client.api.statistics.response.ProcessDefinitionMessageSubscriptionStatistics;
import io.camunda.client.api.statistics.response.ProcessDefinitionMessageSubscriptionStatisticsItem;
import io.camunda.client.api.search.response.OffsetResponse;
import io.camunda.client.api.search.response.OffsetResponsePage;
import io.camunda.client.api.statistics.response.ProcessDefinitionInstanceStatistics;
import io.camunda.client.api.statistics.response.ProcessDefinitionInstanceVersionStatistics;
import io.camunda.client.api.statistics.response.ProcessElementStatistics;
import io.camunda.client.api.statistics.response.UsageMetricsStatistics;
import io.camunda.client.api.statistics.response.UsageMetricsStatisticsItem;
import io.camunda.client.impl.search.response.OffsetResponseImpl;
import io.camunda.client.impl.search.response.OffsetResponsePageImpl;
import io.camunda.client.protocol.rest.ProcessDefinitionElementStatisticsQueryResult;
import io.camunda.client.protocol.rest.ProcessDefinitionMessageSubscriptionStatisticsQueryResult;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceStatisticsPageResponse;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceStatisticsQueryResult;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsQueryResult;
import io.camunda.client.protocol.rest.UsageMetricsResponse;
import io.camunda.client.protocol.rest.UsageMetricsResponseItem;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StatisticsResponseMapper {

  public static ProcessDefinitionMessageSubscriptionStatistics
      toProcessDefinitionMessageSubscriptionStatisticsResponse(
          final ProcessDefinitionMessageSubscriptionStatisticsQueryResult response) {
    final List<ProcessDefinitionMessageSubscriptionStatisticsItem> items =
        Optional.ofNullable(response.getItems())
            .map(
                l ->
                    l.stream()
                        .map(
                            r ->
                                (ProcessDefinitionMessageSubscriptionStatisticsItem)
                                    new ProcessDefinitionMessageSubscriptionStatisticsItemImpl(
                                        r.getProcessDefinitionId(),
                                        r.getProcessDefinitionKey(),
                                        r.getTenantId(),
                                        r.getProcessInstancesWithActiveSubscriptions(),
                                        r.getActiveSubscriptions()))
                        .collect(Collectors.toList()))
            .orElse(Collections.emptyList());
    return new ProcessDefinitionMessageSubscriptionStatisticsImpl(
        items, toSearchResponsePage(response.getPage()));
  }

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

  public static OffsetResponse<ProcessDefinitionInstanceStatistics>
      toProcessDefinitionInstanceStatisticsResponse(
          final ProcessDefinitionInstanceStatisticsQueryResult response) {
    final OffsetResponsePage page = toSearchResponsePage(response.getPage());
    final List<ProcessDefinitionInstanceStatistics> items =
        toSearchResponseInstances(
            response.getItems(), ProcessDefinitionInstanceStatisticsImpl::new);

    return new OffsetResponseImpl<>(items, page);
  }

  public static OffsetResponse<ProcessDefinitionInstanceVersionStatistics>
      toProcessDefinitionInstanceVersionStatisticsResponse(
          final ProcessDefinitionInstanceVersionStatisticsQueryResult response) {
    final OffsetResponsePage page = toSearchResponsePage(response.getPage());
    final List<ProcessDefinitionInstanceVersionStatistics> items =
        toSearchResponseInstances(
            response.getItems(), ProcessDefinitionInstanceVersionStatisticsImpl::new);

    return new OffsetResponseImpl<>(items, page);
  }

  private static OffsetResponsePage toSearchResponsePage(
      final ProcessDefinitionInstanceStatisticsPageResponse pageResponse) {
    if (pageResponse == null) {
      return new OffsetResponsePageImpl(0L, false);
    }
    return new OffsetResponsePageImpl(
        pageResponse.getTotalItems(), pageResponse.getHasMoreTotalItems());
  }

  private static <T, R> List<R> toSearchResponseInstances(
      final List<T> items, final Function<T, R> mapper) {
    return Optional.ofNullable(items)
        .map(i -> i.stream().map(mapper).collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }
}
