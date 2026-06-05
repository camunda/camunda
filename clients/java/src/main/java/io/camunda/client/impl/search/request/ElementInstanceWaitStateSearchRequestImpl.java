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

import static io.camunda.client.api.search.request.SearchRequestBuilders.anyPage;
import static io.camunda.client.api.search.request.SearchRequestBuilders.elementInstanceWaitStateFilter;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.ElementInstanceWaitStateFilter;
import io.camunda.client.api.search.page.AnyPage;
import io.camunda.client.api.search.request.ElementInstanceWaitStateSearchRequest;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.response.ElementInstanceWaitStateResult;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.ElementInstanceWaitStateQuery;
import io.camunda.client.protocol.rest.ElementInstanceWaitStateQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ElementInstanceWaitStateSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<ElementInstanceWaitStateQuery>
    implements ElementInstanceWaitStateSearchRequest {

  private final ElementInstanceWaitStateQuery request;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public ElementInstanceWaitStateSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new ElementInstanceWaitStateQuery();
  }

  @Override
  public FinalSearchRequestStep<ElementInstanceWaitStateResult> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<ElementInstanceWaitStateResult>> send() {
    final HttpCamundaFuture<SearchResponse<ElementInstanceWaitStateResult>> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/element-instances/wait-states/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ElementInstanceWaitStateQueryResult.class,
        SearchResponseMapper::toElementInstanceWaitStateSearchResponse,
        result);
    return result;
  }

  @Override
  public ElementInstanceWaitStateSearchRequest filter(final ElementInstanceWaitStateFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ElementInstanceWaitStateSearchRequest filter(
      final Consumer<ElementInstanceWaitStateFilter> fn) {
    return filter(elementInstanceWaitStateFilter(fn));
  }

  @Override
  public ElementInstanceWaitStateSearchRequest page(final AnyPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ElementInstanceWaitStateSearchRequest page(final Consumer<AnyPage> fn) {
    return page(anyPage(fn));
  }

  @Override
  protected ElementInstanceWaitStateQuery getSearchRequestProperty() {
    return request;
  }
}
