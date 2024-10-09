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
package io.camunda.zeebe.client.impl.search.query;

import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.processDefinitionFilter;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.processDefinitionSort;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.searchRequestPage;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.search.SearchRequestPage;
import io.camunda.zeebe.client.api.search.filter.ProcessDefinitionFilter;
import io.camunda.zeebe.client.api.search.query.FinalSearchQueryStep;
import io.camunda.zeebe.client.api.search.query.ProcessDefinitionQuery;
import io.camunda.zeebe.client.api.search.response.ProcessDefinition;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.client.api.search.sort.ProcessDefinitionSort;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.search.SearchResponseMapper;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.ProcessDefinitionFilterRequest;
import io.camunda.zeebe.client.protocol.rest.ProcessDefinitionSearchQueryRequest;
import io.camunda.zeebe.client.protocol.rest.ProcessDefinitionSearchQueryResponse;
import io.camunda.zeebe.client.protocol.rest.SearchQueryPageRequest;
import io.camunda.zeebe.client.protocol.rest.SearchQuerySortRequest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ProcessDefinitionQueryImpl
    extends TypedSearchRequestPropertyProvider<ProcessDefinitionSearchQueryRequest>
    implements ProcessDefinitionQuery {

  private final ProcessDefinitionSearchQueryRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ProcessDefinitionQueryImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new ProcessDefinitionSearchQueryRequest();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchQueryStep<ProcessDefinition> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<SearchQueryResponse<ProcessDefinition>> send() {
    final HttpZeebeFuture<SearchQueryResponse<ProcessDefinition>> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/process-definitions/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ProcessDefinitionSearchQueryResponse.class,
        SearchResponseMapper::toProcessDefinitionSearchResponse,
        result);
    return result;
  }

  @Override
  public ProcessDefinitionQuery filter(final ProcessDefinitionFilter value) {
    final ProcessDefinitionFilterRequest filter = provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public ProcessDefinitionQuery filter(final Consumer<ProcessDefinitionFilter> fn) {
    return filter(processDefinitionFilter(fn));
  }

  @Override
  public ProcessDefinitionQuery sort(final ProcessDefinitionSort value) {
    final List<SearchQuerySortRequest> sorting = provideSearchRequestProperty(value);
    request.setSort(sorting);
    return this;
  }

  @Override
  public ProcessDefinitionQuery sort(final Consumer<ProcessDefinitionSort> fn) {
    return sort(processDefinitionSort(fn));
  }

  @Override
  public ProcessDefinitionQuery page(final SearchRequestPage value) {
    final SearchQueryPageRequest page = provideSearchRequestProperty(value);
    request.setPage(page);
    return this;
  }

  @Override
  public ProcessDefinitionQuery page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected ProcessDefinitionSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
