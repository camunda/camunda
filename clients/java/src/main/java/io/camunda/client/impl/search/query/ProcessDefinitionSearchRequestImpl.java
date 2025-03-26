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
package io.camunda.client.impl.search.query;

import static io.camunda.client.api.search.request.SearchRequestBuilders.processDefinitionFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.processDefinitionSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.ProcessDefinitionFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.ProcessDefinitionSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.ProcessDefinitionSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.SearchRequestSortMapper;
import io.camunda.client.impl.search.SearchResponseMapper;
import io.camunda.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.ProcessDefinitionSearchQuery;
import io.camunda.client.protocol.rest.ProcessDefinitionSearchQueryResult;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ProcessDefinitionSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<ProcessDefinitionSearchQuery>
    implements ProcessDefinitionSearchRequest {

  private final ProcessDefinitionSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ProcessDefinitionSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new ProcessDefinitionSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<ProcessDefinition> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<ProcessDefinition>> send() {
    final HttpCamundaFuture<SearchResponse<ProcessDefinition>> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/process-definitions/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ProcessDefinitionSearchQueryResult.class,
        SearchResponseMapper::toProcessDefinitionSearchResponse,
        result);
    return result;
  }

  @Override
  public ProcessDefinitionSearchRequest filter(final ProcessDefinitionFilter value) {
    final io.camunda.client.protocol.rest.ProcessDefinitionFilter filter =
        provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public ProcessDefinitionSearchRequest filter(final Consumer<ProcessDefinitionFilter> fn) {
    return filter(processDefinitionFilter(fn));
  }

  @Override
  public ProcessDefinitionSearchRequest sort(final ProcessDefinitionSort value) {
    final List<io.camunda.client.impl.search.SearchRequestSort> sorting =
        provideSearchRequestProperty(value);
    request.setSort(SearchRequestSortMapper.toProcessDefinitionSearchQuerySortRequest(sorting));
    return this;
  }

  @Override
  public ProcessDefinitionSearchRequest sort(final Consumer<ProcessDefinitionSort> fn) {
    return sort(processDefinitionSort(fn));
  }

  @Override
  public ProcessDefinitionSearchRequest page(final SearchRequestPage value) {
    final SearchQueryPageRequest page = provideSearchRequestProperty(value);
    request.setPage(page);
    return this;
  }

  @Override
  public ProcessDefinitionSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected ProcessDefinitionSearchQuery getSearchRequestProperty() {
    return request;
  }
}
