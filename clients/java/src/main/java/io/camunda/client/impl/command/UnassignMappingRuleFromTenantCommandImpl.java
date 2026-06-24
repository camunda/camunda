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

import static io.camunda.client.impl.command.ArgumentUtil.ensureNotNullNorEmpty;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.UnassignMappingRuleFromTenantCommandStep1;
import io.camunda.client.api.command.UnassignMappingRuleFromTenantCommandStep1.UnassignMappingRuleFromTenantCommandStep2;
import io.camunda.client.api.command.UnassignMappingRuleFromTenantCommandStep1.UnassignMappingRuleFromTenantCommandStep3;
import io.camunda.client.api.response.UnassignMappingRuleFromTenantResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.UnassignMappingRuleFromTenantResponseImpl;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class UnassignMappingRuleFromTenantCommandImpl
    implements UnassignMappingRuleFromTenantCommandStep1,
        UnassignMappingRuleFromTenantCommandStep2,
        UnassignMappingRuleFromTenantCommandStep3 {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private String mappingRuleId;
  private String tenantId;

  public UnassignMappingRuleFromTenantCommandImpl(final HttpClient httpClient) {
    this.httpClient = httpClient;
    this.httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public UnassignMappingRuleFromTenantCommandStep2 mappingRuleId(final String mappingRuleId) {
    this.mappingRuleId = mappingRuleId;
    return this;
  }

  @Override
  public UnassignMappingRuleFromTenantCommandStep3 tenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public FinalCommandStep<UnassignMappingRuleFromTenantResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UnassignMappingRuleFromTenantResponse> send() {
    ensureNotNullNorEmpty("mappingRuleId", mappingRuleId);
    ensureNotNullNorEmpty("tenantId", tenantId);
    final HttpCamundaFuture<UnassignMappingRuleFromTenantResponse> result =
        new HttpCamundaFuture<>();
    httpClient.delete(
        "/tenants/" + tenantId + "/mapping-rules/" + mappingRuleId,
        Collections.emptyMap(), // No query parameters needed
        httpRequestConfig.build(),
        UnassignMappingRuleFromTenantResponseImpl::new,
        result);
    return result;
  }
}
