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

import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.processInstanceFilter;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.processInstanceSort;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.searchRequestPage;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.search.SearchRequestPage;
import io.camunda.zeebe.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.client.api.search.query.FinalSearchQueryStep;
import io.camunda.zeebe.client.api.search.query.ProcessInstanceQuery;
import io.camunda.zeebe.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.client.api.search.sort.ProcessInstanceSort;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.search.SearchResponseMapper;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceFilterRequest;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceSearchQueryRequest;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceSearchQueryResponse;
import io.camunda.zeebe.client.protocol.rest.SearchQueryPageRequest;
import io.camunda.zeebe.client.protocol.rest.SearchQuerySortRequest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ProcessInstanceQueryImpl
    extends TypedSearchRequestPropertyProvider<ProcessInstanceSearchQueryRequest>
    implements ProcessInstanceQuery {

  private final ProcessInstanceSearchQueryRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ProcessInstanceQueryImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new ProcessInstanceSearchQueryRequest();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchQueryStep<ProcessInstance> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<SearchQueryResponse<ProcessInstance>> send() {
    final HttpZeebeFuture<SearchQueryResponse<ProcessInstance>> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/process-instances/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ProcessInstanceSearchQueryResponse.class,
        SearchResponseMapper::toProcessInstanceSearchResponse,
        result);
    return result;
  }

  @Override
  public ProcessInstanceQuery filter(final ProcessInstanceFilter value) {
    final ProcessInstanceFilterRequest filter = provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public ProcessInstanceQuery filter(final Consumer<ProcessInstanceFilter> fn) {
    return filter(processInstanceFilter(fn));
  }

  @Override
  public ProcessInstanceQuery sort(final ProcessInstanceSort value) {
    final List<SearchQuerySortRequest> sorting = provideSearchRequestProperty(value);
    request.setSort(sorting);
    return this;
  }

  @Override
  public ProcessInstanceQuery sort(final Consumer<ProcessInstanceSort> fn) {
    return sort(processInstanceSort(fn));
  }

  @Override
  public ProcessInstanceQuery page(final SearchRequestPage value) {
    final SearchQueryPageRequest page = provideSearchRequestProperty(value);
    request.setPage(page);
    return this;
  }

  @Override
  public ProcessInstanceQuery page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected ProcessInstanceSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
