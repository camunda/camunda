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
import io.camunda.client.api.command.AssignRoleToTenantCommandStep1;
import io.camunda.client.api.command.AssignRoleToTenantCommandStep1.AssignRoleToTenantCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.AssignRoleToTenantResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.AssignRoleToTenantResponseImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class AssignRoleToTenantCommandImpl
    implements AssignRoleToTenantCommandStep1, AssignRoleToTenantCommandStep2 {

  private String tenantId;
  private String roleId;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public AssignRoleToTenantCommandImpl(final HttpClient httpClient) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public AssignRoleToTenantCommandStep2 roleId(final String roleId) {
    this.roleId = roleId;
    return this;
  }

  @Override
  public AssignRoleToTenantCommandStep2 tenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public FinalCommandStep<AssignRoleToTenantResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AssignRoleToTenantResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("tenantId", tenantId);
    ArgumentUtil.ensureNotNullNorEmpty("roleId", roleId);
    final HttpCamundaFuture<AssignRoleToTenantResponse> result = new HttpCamundaFuture<>();
    final String endpoint = String.format("/tenants/%s/roles/%s", tenantId, roleId);
    httpClient.put(
        endpoint, null, httpRequestConfig.build(), AssignRoleToTenantResponseImpl::new, result);
    return result;
  }
}
