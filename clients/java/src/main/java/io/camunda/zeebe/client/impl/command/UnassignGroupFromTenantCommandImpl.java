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
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.UnassignGroupFromTenantCommandStep1;
import io.camunda.zeebe.client.api.response.UnassignGroupFromTenantResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class UnassignGroupFromTenantCommandImpl
    implements UnassignGroupFromTenantCommandStep1 {
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long tenantKey;
  private final long groupKey;

  public UnassignGroupFromTenantCommandImpl(
      final HttpClient httpClient, final long tenantKey, final long groupKey) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    this.tenantKey = tenantKey;
    this.groupKey = groupKey;
  }

  @Override
  public FinalCommandStep<UnassignGroupFromTenantResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<UnassignGroupFromTenantResponse> send() {
    final HttpZeebeFuture<UnassignGroupFromTenantResponse> result = new HttpZeebeFuture<>();
    final String endpoint = String.format("/tenants/%d/groups/%d", tenantKey, groupKey);
    httpClient.delete(endpoint, httpRequestConfig.build(), result);
    return result;
  }
}
