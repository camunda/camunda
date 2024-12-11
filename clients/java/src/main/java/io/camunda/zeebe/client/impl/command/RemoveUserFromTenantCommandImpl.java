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
import io.camunda.zeebe.client.api.command.RemoveUserFromTenantCommandStep1;
import io.camunda.zeebe.client.api.response.RemoveUserFromTenantResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class RemoveUserFromTenantCommandImpl implements RemoveUserFromTenantCommandStep1 {

  private final long tenantKey;
  private long userKey;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public RemoveUserFromTenantCommandImpl(final HttpClient httpClient, final long tenantKey) {
    this.httpClient = httpClient;
    this.tenantKey = tenantKey;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public RemoveUserFromTenantCommandStep1 userKey(final long userKey) {
    this.userKey = userKey;
    return this;
  }

  @Override
  public FinalCommandStep<RemoveUserFromTenantResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<RemoveUserFromTenantResponse> send() {
    final HttpZeebeFuture<RemoveUserFromTenantResponse> result = new HttpZeebeFuture<>();
    final String endpoint = String.format("/tenants/%d/users/%d", tenantKey, userKey);
    httpClient.delete(endpoint, null, httpRequestConfig.build(), result);
    return result;
  }
}
