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
package io.camunda.zeebe.client.impl.command;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CreateRoleCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.CreateRoleResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.response.CreateRoleResponseImpl;
import io.camunda.zeebe.client.protocol.rest.RoleCreateRequest;
import io.camunda.zeebe.client.protocol.rest.RoleCreateResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class CreateRoleCommandImpl implements CreateRoleCommandStep1 {
  private final RoleCreateRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateRoleCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new RoleCreateRequest();
  }

  @Override
  public CreateRoleCommandStep1 name(final String name) {
    ArgumentUtil.ensureNotNull("name", name);
    request.setName(name);
    return this;
  }

  @Override
  public FinalCommandStep<CreateRoleResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<CreateRoleResponse> send() {
    // ArgumentUtil.ensureNotNull("name", request.getName());
    final HttpZeebeFuture<CreateRoleResponse> result = new HttpZeebeFuture<>();
    final CreateRoleResponseImpl response = new CreateRoleResponseImpl();
    httpClient.post(
        "/roles",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        RoleCreateResponse.class,
        response::setResponse,
        result);
    return result;
  }
}
