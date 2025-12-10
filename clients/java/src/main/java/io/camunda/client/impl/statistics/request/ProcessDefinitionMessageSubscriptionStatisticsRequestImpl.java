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

import static io.camunda.client.api.search.request.SearchRequestBuilders.messageSubscriptionFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.search.filter.MessageSubscriptionFilter;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.statistics.request.ProcessDefinitionMessageSubscriptionStatisticsRequest;
import io.camunda.client.api.statistics.response.ProcessDefinitionMessageSubscriptionStatistics;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.CursorForwardPagination;
import io.camunda.client.protocol.rest.ProcessDefinitionMessageSubscriptionStatisticsQuery;
import io.camunda.client.protocol.rest.ProcessDefinitionMessageSubscriptionStatisticsQueryResult;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ProcessDefinitionMessageSubscriptionStatisticsRequestImpl
    extends TypedSearchRequestPropertyProvider<ProcessDefinitionMessageSubscriptionStatisticsQuery>
    implements ProcessDefinitionMessageSubscriptionStatisticsRequest {

  private final ProcessDefinitionMessageSubscriptionStatisticsQuery request;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public ProcessDefinitionMessageSubscriptionStatisticsRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new ProcessDefinitionMessageSubscriptionStatisticsQuery();
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalCommandStep<ProcessDefinitionMessageSubscriptionStatistics> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<ProcessDefinitionMessageSubscriptionStatistics> send() {
    final HttpCamundaFuture<ProcessDefinitionMessageSubscriptionStatistics> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/process-definitions/statistics/message-subscriptions",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ProcessDefinitionMessageSubscriptionStatisticsQueryResult.class,
        StatisticsResponseMapper::toProcessDefinitionMessageSubscriptionStatisticsResponse,
        result);
    return result;
  }

  @Override
  public ProcessDefinitionMessageSubscriptionStatisticsRequest filter(
      final MessageSubscriptionFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ProcessDefinitionMessageSubscriptionStatisticsRequest filter(
      final Consumer<MessageSubscriptionFilter> fn) {
    return filter(messageSubscriptionFilter(fn));
  }

  @Override
  protected ProcessDefinitionMessageSubscriptionStatisticsQuery getSearchRequestProperty() {
    return request;
  }

  @Override
  public ProcessDefinitionMessageSubscriptionStatisticsRequest page(final SearchRequestPage value) {
    final SearchQueryPageRequest page = provideSearchRequestProperty(value);
    request.setPage(new CursorForwardPagination().limit(page.getLimit()).after(page.getAfter()));
    return this;
  }

  @Override
  public ProcessDefinitionMessageSubscriptionStatisticsRequest page(
      final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }
}
