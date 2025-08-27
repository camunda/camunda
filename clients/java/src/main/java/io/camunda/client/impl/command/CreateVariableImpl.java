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
import io.camunda.client.api.command.VariableCreationCommandStep1;
import io.camunda.client.api.response.CreateVariableResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.protocol.rest.ClusterVariableCreateRequest;
import io.camunda.client.protocol.rest.CreateVariableRequest;
import io.camunda.client.protocol.rest.VariableScope;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateVariableImpl implements VariableCreationCommandStep1 {

  private CreateVariableRequest createVariableRequest;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateVariableImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    createVariableRequest = new CreateVariableRequest();
  }

  @Override
  public FinalCommandStep<CreateVariableResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateVariableResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("name", createVariableRequest.getName());
    ArgumentUtil.ensureNotNull("value", createVariableRequest.getValue());
    ArgumentUtil.ensureNotNull("scope", createVariableRequest.getScope());
    final HttpCamundaFuture<CreateVariableResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/variables", jsonMapper.toJson(createVariableRequest), httpRequestConfig.build(), result);
    return result;
  }

  @Override
  public VariableCreationCommandStep1 variable(final String key, final Object value) {
    createVariableRequest.setName(key);
    createVariableRequest.setValue(value);
    return this;
  }

  @Override
  public VariableCreationCommandStep1 clusterLevel() {
    createVariableRequest =
        new ClusterVariableCreateRequest()
            .name(createVariableRequest.getName())
            .value(createVariableRequest.getValue())
            .scope(VariableScope.CLUSTER);
    return this;
  }
}
