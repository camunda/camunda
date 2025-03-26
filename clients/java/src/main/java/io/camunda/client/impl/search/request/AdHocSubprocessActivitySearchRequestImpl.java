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

import static io.camunda.client.api.search.request.SearchRequestBuilders.adHocSubprocessActivityFilter;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.search.filter.AdHocSubprocessActivityFilter;
import io.camunda.client.api.search.request.AdHocSubprocessActivitySearchRequest;
import io.camunda.client.api.search.response.AdHocSubprocessActivityResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.AdHocSubprocessActivityResponseImpl;
import io.camunda.client.protocol.rest.AdHocSubprocessActivitySearchQuery;
import io.camunda.client.protocol.rest.AdHocSubprocessActivitySearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class AdHocSubprocessActivitySearchRequestImpl
    implements AdHocSubprocessActivitySearchRequest {

  private final AdHocSubprocessActivitySearchQuery request;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public AdHocSubprocessActivitySearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new AdHocSubprocessActivitySearchQuery();
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public AdHocSubprocessActivitySearchRequest filter(final AdHocSubprocessActivityFilter filter) {
    request.setFilter(filter.getRequestFilter());
    return this;
  }

  @Override
  public AdHocSubprocessActivitySearchRequest filter(
      final Consumer<AdHocSubprocessActivityFilter> fn) {
    return filter(adHocSubprocessActivityFilter(fn));
  }

  @Override
  public FinalCommandStep<AdHocSubprocessActivityResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AdHocSubprocessActivityResponse> send() {
    final HttpCamundaFuture<AdHocSubprocessActivityResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/element-instances/ad-hoc-activities/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        AdHocSubprocessActivitySearchQueryResult.class,
        AdHocSubprocessActivityResponseImpl::new,
        result);
    return result;
  }
}
