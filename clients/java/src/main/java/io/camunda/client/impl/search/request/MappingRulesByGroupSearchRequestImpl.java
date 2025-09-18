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
import io.camunda.client.api.search.request.MappingRulesByGroupSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.MappingRule;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.MappingRuleSort;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.MappingRuleSearchQueryRequest;
import io.camunda.client.protocol.rest.MappingRuleSearchQueryResult;
import java.util.function.Consumer;

public class MappingRulesByGroupSearchRequestImpl
    extends AbstractSearchRequestImpl<MappingRuleSearchQueryRequest, MappingRule>
    implements MappingRulesByGroupSearchRequest {

  private final MappingRuleSearchQueryRequest request;
  private final String groupId;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;

  public MappingRulesByGroupSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String groupId) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.groupId = groupId;
    request = new MappingRuleSearchQueryRequest();
  }

  @Override
  public CamundaFuture<SearchResponse<MappingRule>> send() {
    ArgumentUtil.ensureNotNullNorEmpty("groupId", groupId);

    return httpClient.post(
        String.format("/groups/%s/mapping-rules/search", groupId),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        MappingRuleSearchQueryResult.class,
        SearchResponseMapper::toMappingRulesResponse,
        consistencyPolicy);
  }

  @Override
  public MappingRulesByGroupSearchRequest filter(final MappingRuleFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public MappingRulesByGroupSearchRequest filter(final Consumer<MappingRuleFilter> fn) {
    return filter(mappingRuleFilter(fn));
  }

  @Override
  public MappingRulesByGroupSearchRequest sort(final MappingRuleSort value) {
    request.setSort(
        SearchRequestSortMapper.toMappingRuleSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public MappingRulesByGroupSearchRequest sort(final Consumer<MappingRuleSort> fn) {
    return sort(mappingRuleSort(fn));
  }

  @Override
  public MappingRulesByGroupSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public MappingRulesByGroupSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected MappingRuleSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
