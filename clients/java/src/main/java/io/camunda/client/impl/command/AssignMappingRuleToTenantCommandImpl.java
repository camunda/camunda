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
import io.camunda.client.api.command.AssignMappingRuleToTenantCommandStep1;
import io.camunda.client.api.command.AssignMappingRuleToTenantCommandStep1.AssignMappingRuleToTenantCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.AssignMappingRuleToTenantResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.AssignMappingRuleToTenantResponseImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class AssignMappingRuleToTenantCommandImpl
    implements AssignMappingRuleToTenantCommandStep1, AssignMappingRuleToTenantCommandStep2 {

  private String tenantId;
  private String mappingRuleId;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public AssignMappingRuleToTenantCommandImpl(final HttpClient httpClient) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public AssignMappingRuleToTenantCommandStep2 mappingRuleId(final String mappingRuleId) {
    this.mappingRuleId = mappingRuleId;
    return this;
  }

  @Override
  public AssignMappingRuleToTenantCommandStep2 tenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public FinalCommandStep<AssignMappingRuleToTenantResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AssignMappingRuleToTenantResponse> send() {
    final HttpCamundaFuture<AssignMappingRuleToTenantResponse> result = new HttpCamundaFuture<>();
    final String endpoint = String.format("/tenants/%s/mapping-rules/%s", tenantId, mappingRuleId);
    httpClient.put(
        endpoint,
        null,
        httpRequestConfig.build(),
        AssignMappingRuleToTenantResponseImpl::new,
        result);
    return result;
  }
}
