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

import static io.camunda.client.api.search.request.SearchRequestBuilders.mappingRuleFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.mappingRuleSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.MappingRuleFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.MappingRulesByTenantSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.MappingRule;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.MappingRuleSort;
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

public class MappingRulesByTenantSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<MappingRuleSearchQueryRequest>
    implements MappingRulesByTenantSearchRequest {

  private final MappingRuleSearchQueryRequest request;
  private final String tenantId;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public MappingRulesByTenantSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String tenantId) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.tenantId = tenantId;
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
    ArgumentUtil.ensureNotNullNorEmpty("tenantId", tenantId);
    final HttpCamundaFuture<SearchResponse<MappingRule>> result = new HttpCamundaFuture<>();
    httpClient.post(
        String.format("/tenants/%s/mapping-rules/search", tenantId),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        MappingRuleSearchQueryResult.class,
        SearchResponseMapper::toMappingRulesResponse,
        result);
    return result;
  }

  @Override
  public MappingRulesByTenantSearchRequest filter(final MappingRuleFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public MappingRulesByTenantSearchRequest filter(final Consumer<MappingRuleFilter> fn) {
    return filter(mappingRuleFilter(fn));
  }

  @Override
  public MappingRulesByTenantSearchRequest sort(final MappingRuleSort value) {
    request.setSort(
        SearchRequestSortMapper.toMappingRuleSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public MappingRulesByTenantSearchRequest sort(final Consumer<MappingRuleSort> fn) {
    return sort(mappingRuleSort(fn));
  }

  @Override
  public MappingRulesByTenantSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public MappingRulesByTenantSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected MappingRuleSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
