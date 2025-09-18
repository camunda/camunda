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

import static io.camunda.client.api.search.request.SearchRequestBuilders.processDefinitionFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.processDefinitionSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.ProcessDefinitionFilter;
import io.camunda.client.api.search.request.ProcessDefinitionSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.ProcessDefinitionSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.ProcessDefinitionSearchQuery;
import io.camunda.client.protocol.rest.ProcessDefinitionSearchQueryResult;
import java.util.function.Consumer;

public class ProcessDefinitionSearchRequestImpl
    extends AbstractSearchRequestImpl<ProcessDefinitionSearchQuery, ProcessDefinition>
    implements ProcessDefinitionSearchRequest {

  private final ProcessDefinitionSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;

  public ProcessDefinitionSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    request = new ProcessDefinitionSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
  }

  @Override
  public CamundaFuture<SearchResponse<ProcessDefinition>> send() {

    return httpClient.post(
        "/process-definitions/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ProcessDefinitionSearchQueryResult.class,
        SearchResponseMapper::toProcessDefinitionSearchResponse,
        consistencyPolicy);
  }

  @Override
  public ProcessDefinitionSearchRequest filter(final ProcessDefinitionFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ProcessDefinitionSearchRequest filter(final Consumer<ProcessDefinitionFilter> fn) {
    return filter(processDefinitionFilter(fn));
  }

  @Override
  public ProcessDefinitionSearchRequest sort(final ProcessDefinitionSort value) {
    request.setSort(
        SearchRequestSortMapper.toProcessDefinitionSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public ProcessDefinitionSearchRequest sort(final Consumer<ProcessDefinitionSort> fn) {
    return sort(processDefinitionSort(fn));
  }

  @Override
  public ProcessDefinitionSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
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
