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

import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.AssignGroupToTenantCommandStep1;
import io.camunda.zeebe.client.api.response.AssignGroupToTenantResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class AssignGroupToTenantCommandImpl implements AssignGroupToTenantCommandStep1 {

  private final HttpClient httpClient;
  private final long tenantKey;
  private final long groupKey;
  private final RequestConfig.Builder httpRequestConfig;

  public AssignGroupToTenantCommandImpl(
      final HttpClient httpClient, final long tenantKey, final long groupKey) {
    this.httpClient = httpClient;
    this.tenantKey = tenantKey;
    this.groupKey = groupKey;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public AssignGroupToTenantCommandStep1 requestTimeout(final Duration timeout) {
    httpRequestConfig.setResponseTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<AssignGroupToTenantResponse> send() {
    final HttpZeebeFuture<AssignGroupToTenantResponse> result = new HttpZeebeFuture<>();
    httpClient.put(
        "/tenants/" + tenantKey + "/groups/" + groupKey,
        null, // No request body needed
        httpRequestConfig.build(),
        result);
    return result;
  }
}
