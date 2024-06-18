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
package io.camunda.zeebe.client.impl.search;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.search.ProcessInstanceFilter;
import io.camunda.zeebe.client.api.search.ProcessInstanceQuery;
import io.camunda.zeebe.client.api.search.ProcessInstanceSort;
import io.camunda.zeebe.client.api.search.QueryPage;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceSearchQueryRequest;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceSearchQueryResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ProcessInstanceQueryImpl extends TypedQueryProperty<ProcessInstanceSearchQueryRequest>
    implements ProcessInstanceQuery {

  private final ProcessInstanceSearchQueryRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ProcessInstanceQueryImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new ProcessInstanceSearchQueryRequest();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public ProcessInstanceQuery filter(final ProcessInstanceFilter value) {
    final ProcessInstanceFilterImpl filter = (ProcessInstanceFilterImpl) value;
    request.setFilter(filter.getQueryProperty());
    return this;
  }

  @Override
  public ProcessInstanceQuery filter(final Consumer<ProcessInstanceFilter> fn) {
    final ProcessInstanceFilterImpl filter = new ProcessInstanceFilterImpl();
    fn.accept(filter);
    return filter(filter);
  }

  @Override
  public ProcessInstanceQuery sort(final ProcessInstanceSort value) {
    final ProcessInstanceSortImpl sorting = (ProcessInstanceSortImpl) value;
    request.setSort(sorting.getQueryProperty());
    return this;
  }

  @Override
  public ProcessInstanceQuery sort(final Consumer<ProcessInstanceSort> fn) {
    final ProcessInstanceSortImpl sort = new ProcessInstanceSortImpl();
    fn.accept(sort);
    return sort(sort);
  }

  @Override
  public ProcessInstanceQuery page(final QueryPage value) {
    final QueryPageImpl page = (QueryPageImpl) value;
    request.setPage(page.getQueryProperty());
    return this;
  }

  @Override
  public ProcessInstanceQuery page(final Consumer<QueryPage> fn) {
    final QueryPageImpl page = new QueryPageImpl();
    fn.accept(page);
    return page(page);
  }

  @Override
  protected ProcessInstanceSearchQueryRequest getQueryProperty() {
    return request;
  }

  @Override
  public FinalCommandStep<ProcessInstanceSearchQueryResponse> requestTimeout(
      Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<ProcessInstanceSearchQueryResponse> send() {
    final HttpZeebeFuture<ProcessInstanceSearchQueryResponse> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/process-instances/search", jsonMapper.toJson(request), httpRequestConfig.build(), result);
    return result;
  }
}
