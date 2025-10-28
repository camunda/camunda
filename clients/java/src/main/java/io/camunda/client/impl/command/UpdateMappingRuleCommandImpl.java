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
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.UpdateMappingRuleCommandStep1;
import io.camunda.client.api.command.UpdateMappingRuleCommandStep1.UpdateMappingRuleCommandStep2;
import io.camunda.client.api.command.UpdateMappingRuleCommandStep1.UpdateMappingRuleCommandStep3;
import io.camunda.client.api.command.UpdateMappingRuleCommandStep1.UpdateMappingRuleCommandStep4;
import io.camunda.client.api.response.UpdateMappingRuleResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.UpdateMappingRuleResponseImpl;
import io.camunda.client.protocol.rest.MappingRuleUpdateRequest;
import io.camunda.client.protocol.rest.MappingRuleUpdateResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class UpdateMappingRuleCommandImpl
    implements UpdateMappingRuleCommandStep1,
        UpdateMappingRuleCommandStep2,
        UpdateMappingRuleCommandStep3,
        UpdateMappingRuleCommandStep4 {
  private final String mappingRuleId;
  private final MappingRuleUpdateRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public UpdateMappingRuleCommandImpl(
      final HttpClient httpClient, final String mappingRuleId, final JsonMapper jsonMapper) {
    this.mappingRuleId = mappingRuleId;
    request = new MappingRuleUpdateRequest();
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public UpdateMappingRuleCommandStep2 name(final String name) {
    request.setName(name);
    return this;
  }

  @Override
  public UpdateMappingRuleCommandStep3 claimName(final String claimName) {
    request.setClaimName(claimName);
    return this;
  }

  @Override
  public UpdateMappingRuleCommandStep4 claimValue(final String claimValue) {
    request.setClaimValue(claimValue);
    return this;
  }

  @Override
  public FinalCommandStep<UpdateMappingRuleResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UpdateMappingRuleResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("name", request.getName());
    ArgumentUtil.ensureNotNullNorEmpty("claimName", request.getClaimName());
    ArgumentUtil.ensureNotNullNorEmpty("claimValue", request.getClaimValue());
    final HttpCamundaFuture<UpdateMappingRuleResponse> result = new HttpCamundaFuture<>();
    final UpdateMappingRuleResponseImpl response = new UpdateMappingRuleResponseImpl();

    httpClient.put(
        "/mapping-rules/" + mappingRuleId,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        MappingRuleUpdateResult.class,
        response::setResponse,
        result);
    return result;
  }
}
