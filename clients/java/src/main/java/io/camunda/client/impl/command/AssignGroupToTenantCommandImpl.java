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
import io.camunda.client.api.command.AssignGroupToTenantCommandStep1;
import io.camunda.client.api.response.AssignGroupToTenantResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class AssignGroupToTenantCommandImpl implements AssignGroupToTenantCommandStep1 {

  private final HttpClient httpClient;
  private final String tenantId;
  private final RequestConfig.Builder httpRequestConfig;
  private long groupKey;

  public AssignGroupToTenantCommandImpl(final HttpClient httpClient, final String tenantId) {
    this.httpClient = httpClient;
    this.tenantId = tenantId;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public AssignGroupToTenantCommandStep1 groupKey(final long groupKey) {
    ArgumentUtil.ensureNotNull("groupKey", groupKey);
    this.groupKey = groupKey;
    return this;
  }

  @Override
  public AssignGroupToTenantCommandStep1 requestTimeout(final Duration timeout) {
    httpRequestConfig.setResponseTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AssignGroupToTenantResponse> send() {
    ArgumentUtil.ensureNotNull("groupKey", groupKey);
    final HttpCamundaFuture<AssignGroupToTenantResponse> result = new HttpCamundaFuture<>();
    httpClient.put(
        "/tenants/" + tenantId + "/groups/" + groupKey,
        null, // No request body needed
        httpRequestConfig.build(),
        result);
    return result;
  }
}
