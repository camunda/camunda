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

import static io.camunda.client.api.search.SearchRequestBuilders.decisionRequirementsSort;
import static io.camunda.client.api.search.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.SearchRequestBuilders;
import io.camunda.client.api.search.SearchRequestPage;
import io.camunda.client.api.search.filter.DecisionRequirementsFilter;
import io.camunda.client.api.search.query.DecisionRequirementsSearchRequest;
import io.camunda.client.api.search.query.FinalSearchQueryStep;
import io.camunda.client.api.search.response.DecisionRequirements;
import io.camunda.client.api.search.response.SearchQueryResponse;
import io.camunda.client.api.search.sort.DecisionRequirementsSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.SearchQuerySortRequestMapper;
import io.camunda.client.impl.search.SearchRequestPageImpl;
import io.camunda.client.impl.search.SearchResponseMapper;
import io.camunda.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.search.sort.DecisionRequirementsSortImpl;
import io.camunda.client.protocol.rest.DecisionRequirementsSearchQuery;
import io.camunda.client.protocol.rest.DecisionRequirementsSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class DecisionRequirementsSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<DecisionRequirementsSearchQuery>
    implements DecisionRequirementsSearchRequest {

  private final DecisionRequirementsSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public DecisionRequirementsSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new DecisionRequirementsSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchQueryStep<DecisionRequirements> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public HttpCamundaFuture<SearchQueryResponse<DecisionRequirements>> send() {
    final HttpCamundaFuture<SearchQueryResponse<DecisionRequirements>> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/decision-requirements/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        DecisionRequirementsSearchQueryResult.class,
        SearchResponseMapper::toDecisionRequirementsSearchResponse,
        result);
    return result;
  }

  @Override
  public DecisionRequirementsSearchRequest filter(final DecisionRequirementsFilter value) {
    final io.camunda.client.protocol.rest.DecisionRequirementsFilter filter =
        provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public DecisionRequirementsSearchRequest filter(final Consumer<DecisionRequirementsFilter> fn) {
    return filter(SearchRequestBuilders.decisionRequirementsFilter(fn));
  }

  @Override
  public DecisionRequirementsSearchRequest sort(final DecisionRequirementsSort value) {
    final DecisionRequirementsSortImpl sorting = (DecisionRequirementsSortImpl) value;
    request.setSort(
        SearchQuerySortRequestMapper.toDecisionRequirementsSearchQuerySortRequest(
            sorting.getSearchRequestProperty()));
    return this;
  }

  @Override
  public DecisionRequirementsSearchRequest sort(final Consumer<DecisionRequirementsSort> fn) {
    return sort(decisionRequirementsSort(fn));
  }

  @Override
  public DecisionRequirementsSearchRequest page(final SearchRequestPage value) {
    final SearchRequestPageImpl page = (SearchRequestPageImpl) value;
    request.setPage(page.getSearchRequestProperty());
    return this;
  }

  @Override
  public DecisionRequirementsSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected DecisionRequirementsSearchQuery getSearchRequestProperty() {
    return request;
  }
}
