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

import static io.camunda.client.api.search.request.SearchRequestBuilders.decisionDefinitionFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.decisionDefinitionSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.DecisionDefinitionFilter;
import io.camunda.client.api.search.request.DecisionDefinitionSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.DecisionDefinitionSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.DecisionDefinitionSearchQueryResult;
import java.util.function.Consumer;

public class DecisionDefinitionSearchRequestImpl
    extends AbstractSearchRequestImpl<
        io.camunda.client.protocol.rest.DecisionDefinitionSearchQuery, DecisionDefinition>
    implements DecisionDefinitionSearchRequest {

  private final io.camunda.client.protocol.rest.DecisionDefinitionSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;

  public DecisionDefinitionSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    request = new io.camunda.client.protocol.rest.DecisionDefinitionSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
  }

  @Override
  public DecisionDefinitionSearchRequest filter(final DecisionDefinitionFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public DecisionDefinitionSearchRequest filter(final Consumer<DecisionDefinitionFilter> fn) {
    return filter(decisionDefinitionFilter(fn));
  }

  @Override
  public DecisionDefinitionSearchRequest sort(final DecisionDefinitionSort value) {
    request.setSort(
        SearchRequestSortMapper.toDecisionDefinitionSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public DecisionDefinitionSearchRequest sort(final Consumer<DecisionDefinitionSort> fn) {
    return sort(decisionDefinitionSort(fn));
  }

  @Override
  public DecisionDefinitionSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public DecisionDefinitionSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected io.camunda.client.protocol.rest.DecisionDefinitionSearchQuery
      getSearchRequestProperty() {
    return request;
  }

  @Override
  public CamundaFuture<SearchResponse<DecisionDefinition>> send() {
    return httpClient.post(
        "/decision-definitions/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        DecisionDefinitionSearchQueryResult.class,
        SearchResponseMapper::toDecisionDefinitionSearchResponse,
        consistencyPolicy);
  }
}
