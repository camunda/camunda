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
import io.camunda.client.api.command.CreateMappingRuleCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.CreateMappingRuleResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateMappingRuleResponseImpl;
import io.camunda.client.protocol.rest.MappingRuleCreateRequest;
import io.camunda.client.protocol.rest.MappingRuleCreateResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateMappingRuleCommandImpl implements CreateMappingRuleCommandStep1 {

  private final MappingRuleCreateRequest mappingRuleRequest;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateMappingRuleCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    mappingRuleRequest = new MappingRuleCreateRequest();
  }

  @Override
  public CreateMappingRuleCommandStep1 claimName(final String claimName) {
    mappingRuleRequest.claimName(claimName);
    return this;
  }

  @Override
  public CreateMappingRuleCommandStep1 claimValue(final String claimValue) {
    mappingRuleRequest.claimValue(claimValue);
    return this;
  }

  @Override
  public CreateMappingRuleCommandStep1 name(final String name) {
    mappingRuleRequest.name(name);
    return this;
  }

  @Override
  public CreateMappingRuleCommandStep1 mappingRuleId(final String mappingRuleId) {
    mappingRuleRequest.mappingRuleId(mappingRuleId);
    return this;
  }

  @Override
  public FinalCommandStep<CreateMappingRuleResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateMappingRuleResponse> send() {
    ArgumentUtil.ensureNotNull("claimName", mappingRuleRequest.getClaimName());
    ArgumentUtil.ensureNotNull("claimValue", mappingRuleRequest.getClaimValue());
    ArgumentUtil.ensureNotNull("name", mappingRuleRequest.getName());
    ArgumentUtil.ensureNotNull("mappingRuleId", mappingRuleRequest.getMappingRuleId());
    final HttpCamundaFuture<CreateMappingRuleResponse> result = new HttpCamundaFuture<>();
    final CreateMappingRuleResponseImpl response = new CreateMappingRuleResponseImpl();
    httpClient.post(
        "/mapping-rules",
        jsonMapper.toJson(mappingRuleRequest),
        httpRequestConfig.build(),
        MappingRuleCreateResult.class,
        response::setResponse,
        result);
    return result;
  }
}
