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
import io.camunda.client.api.command.CreateTenantCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.CreateTenantResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateTenantResponseImpl;
import io.camunda.client.protocol.rest.TenantCreateRequest;
import io.camunda.client.protocol.rest.TenantCreateResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class CreateTenantCommandImpl implements CreateTenantCommandStep1 {
  private final TenantCreateRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateTenantCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new TenantCreateRequest();
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public CreateTenantCommandStep1 tenantId(final String tenantId) {
    request.setTenantId(tenantId);
    return this;
  }

  @Override
  public CreateTenantCommandStep1 name(final String name) {
    request.setName(name);
    return this;
  }

  @Override
  public CreateTenantCommandStep1 description(final String description) {
    request.setDescription(description);
    return this;
  }

  @Override
  public FinalCommandStep<CreateTenantResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateTenantResponse> send() {
    ArgumentUtil.ensureNotNull("tenantId", request.getTenantId());
    final HttpCamundaFuture<CreateTenantResponse> result = new HttpCamundaFuture<>();
    final CreateTenantResponseImpl response = new CreateTenantResponseImpl();
    httpClient.post(
        "/tenants",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        TenantCreateResult.class,
        response::setResponse,
        result);
    return result;
  }
}
