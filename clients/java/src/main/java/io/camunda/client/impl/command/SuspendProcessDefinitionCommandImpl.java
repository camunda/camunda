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

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.SuspendProcessDefinitionCommandStep1;
import io.camunda.client.api.response.SuspendProcessDefinitionResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.SuspendProcessDefinitionResponseImpl;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.ProcessDefinitionSuspensionInstruction;
import io.camunda.client.protocol.rest.ProcessDefinitionSuspensionResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class SuspendProcessDefinitionCommandImpl
    implements SuspendProcessDefinitionCommandStep1,
        SuspendProcessDefinitionCommandStep1.SuspendProcessDefinitionCommandStep3 {

  private final ProcessDefinitionSuspensionInstruction httpRequestObject =
      new ProcessDefinitionSuspensionInstruction();
  private final CamundaClientConfiguration config;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public SuspendProcessDefinitionCommandImpl(
      final CamundaClientConfiguration config,
      final HttpClient httpClient,
      final JsonMapper jsonMapper) {
    this.config = config;
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    requestTimeout(config.getDefaultRequestTimeout());
  }

  @Override
  public SuspendProcessDefinitionCommandStep2 processDefinitionKey(
      final long processDefinitionKey) {
    httpRequestObject.setProcessDefinitionKey(ParseUtil.keyToString(processDefinitionKey));
    return this;
  }

  @Override
  public SuspendProcessDefinitionCommandStep3 processDefinitionId(
      final String processDefinitionId) {
    ArgumentUtil.ensureNotNullNorEmpty("processDefinitionId", processDefinitionId);
    httpRequestObject.setProcessDefinitionId(processDefinitionId);
    httpRequestObject.setTenantId(config.getDefaultTenantId());
    return this;
  }

  @Override
  public SuspendProcessDefinitionCommandStep3 version(final int version) {
    ArgumentUtil.ensureGreaterThan("version", version, 0);
    httpRequestObject.setProcessDefinitionVersion(version);
    return this;
  }

  @Override
  public SuspendProcessDefinitionCommandStep3 tenantId(final String tenantId) {
    httpRequestObject.setTenantId(tenantId);
    return this;
  }

  @Override
  public SuspendProcessDefinitionCommandStep3 includeProcessInstances(
      final boolean includeProcessInstances) {
    httpRequestObject.setIncludeProcessInstances(includeProcessInstances);
    return this;
  }

  @Override
  public FinalCommandStep<SuspendProcessDefinitionResponse> requestTimeout(
      final Duration requestTimeout) {
    ArgumentUtil.ensurePositive("requestTimeout", requestTimeout);
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SuspendProcessDefinitionResponse> send() {
    final HttpCamundaFuture<SuspendProcessDefinitionResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/process-definitions/suspension",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        ProcessDefinitionSuspensionResult.class,
        SuspendProcessDefinitionResponseImpl::new,
        result);
    return result;
  }
}
