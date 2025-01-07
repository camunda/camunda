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

import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.decisionRequirementsSort;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.searchRequestPage;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.search.SearchRequestBuilders;
import io.camunda.zeebe.client.api.search.SearchRequestPage;
import io.camunda.zeebe.client.api.search.filter.DecisionRequirementsFilter;
import io.camunda.zeebe.client.api.search.query.DecisionRequirementsQuery;
import io.camunda.zeebe.client.api.search.query.FinalSearchQueryStep;
import io.camunda.zeebe.client.api.search.response.DecisionRequirements;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.client.api.search.sort.DecisionRequirementsSort;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.search.SearchRequestPageImpl;
import io.camunda.zeebe.client.impl.search.SearchResponseMapper;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.impl.search.sort.DecisionRequirementsSortImpl;
import io.camunda.zeebe.client.protocol.rest.DecisionRequirementsFilterRequest;
import io.camunda.zeebe.client.protocol.rest.DecisionRequirementsSearchQueryRequest;
import io.camunda.zeebe.client.protocol.rest.DecisionRequirementsSearchQueryResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class DecisionRequirementsQueryImpl
    extends TypedSearchRequestPropertyProvider<DecisionRequirementsSearchQueryRequest>
    implements DecisionRequirementsQuery {

  private final DecisionRequirementsSearchQueryRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public DecisionRequirementsQueryImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new DecisionRequirementsSearchQueryRequest();
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
  public HttpZeebeFuture<SearchQueryResponse<DecisionRequirements>> send() {
    final HttpZeebeFuture<SearchQueryResponse<DecisionRequirements>> result =
        new HttpZeebeFuture<>();
    httpClient.post(
        "/decision-requirements/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        DecisionRequirementsSearchQueryResponse.class,
        SearchResponseMapper::toDecisionRequirementsSearchResponse,
        result);
    return result;
  }

  @Override
  public DecisionRequirementsQuery filter(final DecisionRequirementsFilter value) {
    final DecisionRequirementsFilterRequest filter = provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public DecisionRequirementsQuery filter(final Consumer<DecisionRequirementsFilter> fn) {
    return filter(SearchRequestBuilders.decisionRequirementsFilter(fn));
  }

  @Override
  public DecisionRequirementsQuery sort(final DecisionRequirementsSort value) {
    final DecisionRequirementsSortImpl sorting = (DecisionRequirementsSortImpl) value;
    request.setSort(sorting.getSearchRequestProperty());
    return this;
  }

  @Override
  public DecisionRequirementsQuery sort(final Consumer<DecisionRequirementsSort> fn) {
    return sort(decisionRequirementsSort(fn));
  }

  @Override
  public DecisionRequirementsQuery page(final SearchRequestPage value) {
    final SearchRequestPageImpl page = (SearchRequestPageImpl) value;
    request.setPage(page.getSearchRequestProperty());
    return this;
  }

  @Override
  public DecisionRequirementsQuery page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected DecisionRequirementsSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
