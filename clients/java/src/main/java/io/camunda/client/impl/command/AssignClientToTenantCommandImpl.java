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
import io.camunda.client.api.command.AssignClientToTenantCommandStep1;
import io.camunda.client.api.command.AssignClientToTenantCommandStep1.AssignClientToTenantCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.AssignClientToTenantResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class AssignClientToTenantCommandImpl
    implements AssignClientToTenantCommandStep1, AssignClientToTenantCommandStep2 {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private String clientId;
  private String tenantId;

  public AssignClientToTenantCommandImpl(final HttpClient httpClient) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public AssignClientToTenantCommandStep2 clientId(final String clientId) {
    this.clientId = clientId;
    return this;
  }

  @Override
  public AssignClientToTenantCommandStep2 tenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public FinalCommandStep<AssignClientToTenantResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AssignClientToTenantResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("clientId", clientId);
    ArgumentUtil.ensureNotNullNorEmpty("tenantId", tenantId);
    final HttpCamundaFuture<AssignClientToTenantResponse> result = new HttpCamundaFuture<>();
    httpClient.put(
        "/tenants/" + tenantId + "/clients/" + clientId,
        null, // No request body needed
        httpRequestConfig.build(),
        result);
    return result;
  }
}
