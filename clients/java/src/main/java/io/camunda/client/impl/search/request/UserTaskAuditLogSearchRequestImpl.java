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

import static io.camunda.client.api.search.request.SearchRequestBuilders.auditLogSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;
import static io.camunda.client.api.search.request.SearchRequestBuilders.userTaskAuditLogFilter;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.UserTaskAuditLogFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.request.UserTaskAuditLogSearchRequest;
import io.camunda.client.api.search.response.AuditLogResult;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.AuditLogSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.AuditLogSearchQueryResult;
import io.camunda.client.protocol.rest.UserTaskAuditLogSearchQueryRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class UserTaskAuditLogSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<UserTaskAuditLogSearchQueryRequest>
    implements UserTaskAuditLogSearchRequest {

  private final UserTaskAuditLogSearchQueryRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long userTaskKey;

  public UserTaskAuditLogSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long userTaskKey) {
    request = new UserTaskAuditLogSearchQueryRequest();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    this.userTaskKey = userTaskKey;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<AuditLogResult> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public HttpCamundaFuture<SearchResponse<AuditLogResult>> send() {
    final HttpCamundaFuture<SearchResponse<AuditLogResult>> result = new HttpCamundaFuture<>();
    httpClient.post(
        String.format("/user-tasks/%d/audit-logs/search", userTaskKey),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        AuditLogSearchQueryResult.class,
        SearchResponseMapper::toAuditLogSearchResponse,
        result);
    return result;
  }

  @Override
  public UserTaskAuditLogSearchRequest filter(final UserTaskAuditLogFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public UserTaskAuditLogSearchRequest filter(final Consumer<UserTaskAuditLogFilter> fn) {
    return filter(userTaskAuditLogFilter(fn));
  }

  @Override
  public UserTaskAuditLogSearchRequest sort(final AuditLogSort value) {
    request.setSort(
        SearchRequestSortMapper.toAuditLogSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public UserTaskAuditLogSearchRequest sort(final Consumer<AuditLogSort> fn) {
    return sort(auditLogSort(fn));
  }

  @Override
  public UserTaskAuditLogSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public UserTaskAuditLogSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected UserTaskAuditLogSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
