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
import io.camunda.client.api.command.TenantScopedClusterVariableDeletionCommandStep1;
import io.camunda.client.api.response.DeleteClusterVariableResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.DeleteClusterVariableResponseImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class TenantScopedDeleteClusterVariableImpl
    implements TenantScopedClusterVariableDeletionCommandStep1 {

  private String name;
  private final String tenantId;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public TenantScopedDeleteClusterVariableImpl(final HttpClient httpClient, final String tenantId) {
    ArgumentUtil.ensureNotNullNorEmpty("tenantId", tenantId);
    this.httpClient = httpClient;
    this.tenantId = tenantId;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public TenantScopedClusterVariableDeletionCommandStep1 delete(final String name) {
    ArgumentUtil.ensureNotNullNorEmpty("name", name);
    this.name = name;
    return this;
  }

  @Override
  public TenantScopedClusterVariableDeletionCommandStep1 requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<DeleteClusterVariableResponse> send() {
    final HttpCamundaFuture<DeleteClusterVariableResponse> result = new HttpCamundaFuture<>();
    final String path = "/cluster-variables/tenants/" + tenantId + "/" + name;
    httpClient.delete(
        path, httpRequestConfig.build(), DeleteClusterVariableResponseImpl::new, result);
    return result;
  }
}
