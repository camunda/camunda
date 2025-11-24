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

import static io.camunda.client.api.search.request.SearchRequestBuilders.clusterVariableFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.clusterVariableSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.ClusterVariableFilter;
import io.camunda.client.api.search.request.ClusterVariableSearchRequest;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.ClusterVariable;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.ClusterVariableSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.ClusterVariableSearchQueryRequest;
import io.camunda.client.protocol.rest.ClusterVariableSearchQueryResult;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ClusterVariableSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<ClusterVariableSearchQueryRequest>
    implements ClusterVariableSearchRequest {

  private final ClusterVariableSearchQueryRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private boolean withFullValues = false;

  public ClusterVariableSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new ClusterVariableSearchQueryRequest();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<ClusterVariable> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<ClusterVariable>> send() {
    final HttpCamundaFuture<SearchResponse<ClusterVariable>> result = new HttpCamundaFuture<>();
    final Map<String, String> queryParams = new HashMap<>();
    if (withFullValues) {
      queryParams.put("truncateValues", String.valueOf(false));
    }
    httpClient.post(
        "/cluster-variables/search",
        queryParams,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ClusterVariableSearchQueryResult.class,
        SearchResponseMapper::toClusterVariableSearchResponse,
        result);
    return result;
  }

  @Override
  public ClusterVariableSearchRequest filter(final ClusterVariableFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ClusterVariableSearchRequest filter(final Consumer<ClusterVariableFilter> fn) {
    return filter(clusterVariableFilter(fn));
  }

  @Override
  public ClusterVariableSearchRequest sort(final ClusterVariableSort value) {
    request.setSort(
        SearchRequestSortMapper.toClusterVariableSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public ClusterVariableSearchRequest sort(final Consumer<ClusterVariableSort> fn) {
    return sort(clusterVariableSort(fn));
  }

  @Override
  public ClusterVariableSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ClusterVariableSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected ClusterVariableSearchQueryRequest getSearchRequestProperty() {
    return request;
  }

  @Override
  public ClusterVariableSearchRequest withFullValues() {
    withFullValues = true;
    return this;
  }
}
