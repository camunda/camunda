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
package io.camunda.client.impl.search.request;

import static io.camunda.client.api.search.request.SearchRequestBuilders.agentInstanceHistoryFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.agentInstanceHistorySort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.anyPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.AgentInstanceHistoryFilter;
import io.camunda.client.api.search.page.AnyPage;
import io.camunda.client.api.search.request.AgentInstanceHistorySearchRequest;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.response.AgentInstanceHistory;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.AgentInstanceHistorySort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.AgentInstanceHistorySearchQuery;
import io.camunda.client.protocol.rest.AgentInstanceHistorySearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class AgentInstanceHistorySearchRequestImpl
    extends TypedSearchRequestPropertyProvider<AgentInstanceHistorySearchQuery>
    implements AgentInstanceHistorySearchRequest {

  private final AgentInstanceHistorySearchQuery request;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;
  private final long agentInstanceKey;

  public AgentInstanceHistorySearchRequestImpl(
      final long agentInstanceKey, final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.agentInstanceKey = agentInstanceKey;
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new AgentInstanceHistorySearchQuery();
  }

  @Override
  public FinalSearchRequestStep<AgentInstanceHistory> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<AgentInstanceHistory>> send() {
    final HttpCamundaFuture<SearchResponse<AgentInstanceHistory>> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/agent-instances/" + agentInstanceKey + "/history/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        AgentInstanceHistorySearchQueryResult.class,
        SearchResponseMapper::toAgentInstanceHistorySearchResponse,
        result);
    return result;
  }

  @Override
  public AgentInstanceHistorySearchRequest filter(final AgentInstanceHistoryFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AgentInstanceHistorySearchRequest filter(final Consumer<AgentInstanceHistoryFilter> fn) {
    return filter(agentInstanceHistoryFilter(fn));
  }

  @Override
  public AgentInstanceHistorySearchRequest page(final AnyPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AgentInstanceHistorySearchRequest page(final Consumer<AnyPage> fn) {
    return page(anyPage(fn));
  }

  @Override
  public AgentInstanceHistorySearchRequest sort(final AgentInstanceHistorySort value) {
    request.setSort(
        SearchRequestSortMapper.toAgentInstanceHistorySearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public AgentInstanceHistorySearchRequest sort(final Consumer<AgentInstanceHistorySort> fn) {
    return sort(agentInstanceHistorySort(fn));
  }

  @Override
  protected AgentInstanceHistorySearchQuery getSearchRequestProperty() {
    return request;
  }
}
