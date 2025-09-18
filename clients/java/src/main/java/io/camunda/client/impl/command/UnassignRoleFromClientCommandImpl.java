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
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.UnassignRoleFromClientCommandStep1;
import io.camunda.client.api.command.UnassignRoleFromClientCommandStep1.UnassignRoleFromClientCommandStep2;
import io.camunda.client.api.command.UnassignRoleFromClientCommandStep1.UnassignRoleFromClientCommandStep3;
import io.camunda.client.api.response.UnassignRoleFromClientResponse;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.UnassignRoleFromClientResponseImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class UnassignRoleFromClientCommandImpl
    implements UnassignRoleFromClientCommandStep1,
        UnassignRoleFromClientCommandStep2,
        UnassignRoleFromClientCommandStep3 {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private String roleId;
  private String clientId;

  public UnassignRoleFromClientCommandImpl(final HttpClient httpClient) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public UnassignRoleFromClientCommandStep2 roleId(final String roleId) {
    this.roleId = roleId;
    return this;
  }

  @Override
  public UnassignRoleFromClientCommandStep3 clientId(final String clientId) {
    this.clientId = clientId;
    return this;
  }

  @Override
  public FinalCommandStep<UnassignRoleFromClientResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UnassignRoleFromClientResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("roleId", roleId);
    ArgumentUtil.ensureNotNullNorEmpty("clientId", clientId);
    return httpClient.delete(
        "/roles/" + roleId + "/clients/" + clientId,
        null, // No request body needed
        httpRequestConfig.build(),
        UnassignRoleFromClientResponseImpl::new);
  }
}
