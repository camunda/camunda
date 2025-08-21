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
import io.camunda.client.api.command.UnassignRoleFromTenantCommandStep1;
import io.camunda.client.api.command.UnassignRoleFromTenantCommandStep1.UnassignRoleFromTenantCommandStep2;
import io.camunda.client.api.response.UnassignRoleFromTenantResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.UnassignRoleFromTenantResponseImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class UnassignRoleFromTenantCommandImpl
    implements UnassignRoleFromTenantCommandStep1, UnassignRoleFromTenantCommandStep2 {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private String roleId;
  private String tenantId;

  public UnassignRoleFromTenantCommandImpl(final HttpClient httpClient) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public UnassignRoleFromTenantCommandStep2 roleId(final String roleId) {
    this.roleId = roleId;
    return this;
  }

  @Override
  public UnassignRoleFromTenantCommandStep2 tenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public FinalCommandStep<UnassignRoleFromTenantResponse> requestTimeout(final Duration timeout) {
    httpRequestConfig.setResponseTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UnassignRoleFromTenantResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("tenantId", tenantId);
    ArgumentUtil.ensureNotNullNorEmpty("roleId", roleId);
    final HttpCamundaFuture<UnassignRoleFromTenantResponse> result = new HttpCamundaFuture<>();
    httpClient.delete(
        String.format("/tenants/%s/roles/%s", tenantId, roleId),
        null,
        httpRequestConfig.build(),
        UnassignRoleFromTenantResponseImpl::new,
        result);
    return result;
  }
}
