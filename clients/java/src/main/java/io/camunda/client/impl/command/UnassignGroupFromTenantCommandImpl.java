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
import io.camunda.client.api.command.UnassignGroupFromTenantCommandStep1;
import io.camunda.client.api.command.UnassignGroupFromTenantCommandStep1.UnassignGroupFromTenantCommandStep2;
import io.camunda.client.api.command.UnassignGroupFromTenantCommandStep1.UnassignGroupFromTenantCommandStep3;
import io.camunda.client.api.response.UnassignGroupFromTenantResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.UnassignGroupFromTenantResponseImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class UnassignGroupFromTenantCommandImpl
    implements UnassignGroupFromTenantCommandStep1,
        UnassignGroupFromTenantCommandStep2,
        UnassignGroupFromTenantCommandStep3 {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private String tenantId;
  private String groupId;

  public UnassignGroupFromTenantCommandImpl(final HttpClient httpClient) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public UnassignGroupFromTenantCommandStep2 groupId(final String groupId) {
    this.groupId = groupId;
    return this;
  }

  @Override
  public UnassignGroupFromTenantCommandStep3 tenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public FinalCommandStep<UnassignGroupFromTenantResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UnassignGroupFromTenantResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("tenantId", tenantId);
    ArgumentUtil.ensureNotNullNorEmpty("groupId", groupId);
    final HttpCamundaFuture<UnassignGroupFromTenantResponse> result = new HttpCamundaFuture<>();
    final String endpoint = String.format("/tenants/%s/groups/%s", tenantId, groupId);
    httpClient.delete(
        endpoint, httpRequestConfig.build(), UnassignGroupFromTenantResponseImpl::new, result);
    return result;
  }
}
