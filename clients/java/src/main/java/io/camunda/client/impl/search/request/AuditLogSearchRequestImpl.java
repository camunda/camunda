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

import static io.camunda.client.api.search.request.SearchRequestBuilders.auditLogFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.auditLogSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.AuditLogFilter;
import io.camunda.client.api.search.request.AuditLogSearchRequest;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.AuditLogResult;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.AuditLogSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.AuditLogSearchQueryRequest;
import io.camunda.client.protocol.rest.AuditLogSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class AuditLogSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<AuditLogSearchQueryRequest>
    implements AuditLogSearchRequest {

  private final AuditLogSearchQueryRequest request;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public AuditLogSearchRequestImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new AuditLogSearchQueryRequest();
  }

  @Override
  public FinalSearchRequestStep<AuditLogResult> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<AuditLogResult>> send() {
    final HttpCamundaFuture<SearchResponse<AuditLogResult>> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/audit-logs/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        AuditLogSearchQueryResult.class,
        SearchResponseMapper::toAuditLogSearchResponse,
        result);
    return result;
  }

  @Override
  public AuditLogSearchRequest filter(final AuditLogFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuditLogSearchRequest filter(final Consumer<AuditLogFilter> fn) {
    return filter(auditLogFilter(fn));
  }

  @Override
  public AuditLogSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuditLogSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  public AuditLogSearchRequest sort(final AuditLogSort value) {
    request.setSort(
        SearchRequestSortMapper.toAuditLogSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public AuditLogSearchRequest sort(final Consumer<AuditLogSort> fn) {
    return sort(auditLogSort(fn));
  }

  @Override
  protected AuditLogSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
