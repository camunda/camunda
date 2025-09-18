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
import io.camunda.client.api.command.CreateRoleCommandStep1;
import io.camunda.client.api.command.CreateRoleCommandStep1.CreateRoleCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.CreateRoleResponse;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateRoleResponseImpl;
import io.camunda.client.protocol.rest.RoleCreateRequest;
import io.camunda.client.protocol.rest.RoleCreateResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class CreateRoleCommandImpl implements CreateRoleCommandStep1, CreateRoleCommandStep2 {
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
  public CreateRoleCommandStep2 roleId(final String roleId) {
    request.setRoleId(roleId);
    return this;
  }

  @Override
  public CreateRoleCommandStep2 name(final String name) {
    request.name(name);
    return this;
  }

  @Override
  public CreateRoleCommandStep2 description(final String description) {
    request.description(description);
    return this;
  }

  @Override
  public FinalCommandStep<CreateRoleResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateRoleResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("roleId", request.getRoleId());
    ArgumentUtil.ensureNotNullNorEmpty("name", request.getName());
    final CreateRoleResponseImpl response = new CreateRoleResponseImpl();
    return httpClient.post(
        "/roles",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        RoleCreateResult.class,
        response::setResponse);
  }
}
