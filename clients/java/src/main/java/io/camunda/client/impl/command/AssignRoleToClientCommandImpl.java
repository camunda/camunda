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
import io.camunda.client.api.command.AssignRoleToClientCommandStep1;
import io.camunda.client.api.command.AssignRoleToClientCommandStep1.AssignRoleToClientCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.AssignRoleToClientResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.AssignRoleToClientResponseImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class AssignRoleToClientCommandImpl
    implements AssignRoleToClientCommandStep1, AssignRoleToClientCommandStep2 {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private String roleId;
  private String clientId;

  public AssignRoleToClientCommandImpl(final HttpClient httpClient) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public AssignRoleToClientCommandStep2 roleId(final String roleId) {
    this.roleId = roleId;
    return this;
  }

  @Override
  public AssignRoleToClientCommandStep2 clientId(final String clientId) {
    this.clientId = clientId;
    return this;
  }

  @Override
  public FinalCommandStep<AssignRoleToClientResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AssignRoleToClientResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("roleId", roleId);
    ArgumentUtil.ensureNotNullNorEmpty("clientId", clientId);
    final HttpCamundaFuture<AssignRoleToClientResponse> result = new HttpCamundaFuture<>();
    httpClient.put(
        "/roles/" + roleId + "/clients/" + clientId,
        null, // No request body needed
        httpRequestConfig.build(),
        AssignRoleToClientResponseImpl::new,
        result);
    return result;
  }
}
