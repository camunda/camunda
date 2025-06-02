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

import static io.camunda.client.api.search.request.SearchRequestBuilders.mappingFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.mappingSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.fetch.MappingsByGroupSearchRequest;
import io.camunda.client.api.search.filter.MappingFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.MappingRule;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.MappingSort;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.MappingRuleSearchQueryRequest;
import io.camunda.client.protocol.rest.MappingRuleSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class MappingsByGroupSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<MappingRuleSearchQueryRequest>
    implements MappingsByGroupSearchRequest {

  private final MappingRuleSearchQueryRequest request;
  private final String groupId;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public MappingsByGroupSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String groupId) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.groupId = groupId;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new MappingRuleSearchQueryRequest();
  }

  @Override
  public FinalSearchRequestStep<MappingRule> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<MappingRule>> send() {
    ArgumentUtil.ensureNotNullNorEmpty("groupId", groupId);
    final HttpCamundaFuture<SearchResponse<MappingRule>> result = new HttpCamundaFuture<>();
    httpClient.post(
        String.format("/groups/%s/mapping-rules/search", groupId),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        MappingRuleSearchQueryResult.class,
        SearchResponseMapper::toMappingsResponse,
        result);
    return result;
  }

  @Override
  public MappingsByGroupSearchRequest filter(final MappingFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public MappingsByGroupSearchRequest filter(final Consumer<MappingFilter> fn) {
    return filter(mappingFilter(fn));
  }

  @Override
  public MappingsByGroupSearchRequest sort(final MappingSort value) {
    request.setSort(
        SearchRequestSortMapper.toMappingSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public MappingsByGroupSearchRequest sort(final Consumer<MappingSort> fn) {
    return sort(mappingSort(fn));
  }

  @Override
  public MappingsByGroupSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public MappingsByGroupSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected MappingRuleSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
