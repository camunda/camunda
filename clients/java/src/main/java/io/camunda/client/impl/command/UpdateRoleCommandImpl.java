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
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.UpdateRoleCommandStep1;
import io.camunda.client.api.response.UpdateRoleResponse;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.UpdateRoleResponseImpl;
import io.camunda.client.protocol.rest.RoleUpdateRequest;
import io.camunda.client.protocol.rest.RoleUpdateResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class UpdateRoleCommandImpl implements UpdateRoleCommandStep1 {
  private final String roleId;
  private final RoleUpdateRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public UpdateRoleCommandImpl(
      final HttpClient httpClient, final String roleId, final JsonMapper jsonMapper) {
    this.roleId = roleId;
    request = new RoleUpdateRequest();
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public UpdateRoleCommandStep1 name(final String name) {
    request.setName(name);
    return this;
  }

  @Override
  public UpdateRoleCommandStep1 description(final String description) {
    request.setDescription(description);
    return this;
  }

  @Override
  public FinalCommandStep<UpdateRoleResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UpdateRoleResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("name", request.getName());
    ArgumentUtil.ensureNotNull("description", request.getDescription());
    final UpdateRoleResponseImpl response = new UpdateRoleResponseImpl();

    return httpClient.put(
        "/roles/" + roleId,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        RoleUpdateResult.class,
        response::setResponse);
  }
}
