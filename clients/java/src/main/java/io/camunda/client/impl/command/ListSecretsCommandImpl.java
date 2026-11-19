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
package io.camunda.client.impl.command;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ListSecretsCommandStep1;
import io.camunda.client.api.response.ListSecretsResponse;
import io.camunda.client.api.search.filter.SecretListFilter;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.ListSecretsResponseImpl;
import io.camunda.client.impl.search.filter.SecretListFilterImpl;
import io.camunda.client.protocol.rest.SecretListRequest;
import io.camunda.client.protocol.rest.SecretListResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ListSecretsCommandImpl implements ListSecretsCommandStep1 {

  private final SecretListRequest request = new SecretListRequest();
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ListSecretsCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public ListSecretsCommandStep1 filter(final Consumer<SecretListFilter> fn) {
    ArgumentUtil.ensureNotNull("fn", fn);
    final SecretListFilterImpl filter = new SecretListFilterImpl();
    fn.accept(filter);
    // A filter without criteria is left out: it lists every state anyway, and clusters before 8.11
    // reject the filter property itself, so sending it would fail there for nothing.
    request.setFilter(filter.isEmpty() ? null : filter.getProtocolObject());
    return this;
  }

  @Override
  public ListSecretsCommandStep1 requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<ListSecretsResponse> send() {
    final HttpCamundaFuture<ListSecretsResponse> result = new HttpCamundaFuture<>();
    final String path = "/secrets/list";
    // Always sent as a JSON body, an empty object without a filter: the endpoint only accepts
    // JSON, and a request without a body carries no content type, so it would be rejected as an
    // unsupported media type.
    httpClient.post(
        path,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        SecretListResult.class,
        ListSecretsResponseImpl::new,
        result);
    return result;
  }
}
