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
import io.camunda.client.api.command.GloballyScopedClusterVariableCreationCommandStep1;
import io.camunda.client.api.response.CreateClusterVariableResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateClusterVariableResponseImpl;
import io.camunda.client.protocol.rest.ClusterVariableResult;
import io.camunda.client.protocol.rest.CreateClusterVariableRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class GloballyScopedCreateClusterVariableImpl
    implements GloballyScopedClusterVariableCreationCommandStep1 {

  private final CreateClusterVariableRequest createVariableRequest;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public GloballyScopedCreateClusterVariableImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    createVariableRequest = new CreateClusterVariableRequest();
  }

  @Override
  public GloballyScopedClusterVariableCreationCommandStep1 create(
      final String name, final Object value) {
    ArgumentUtil.ensureNotNullNorEmpty("name", name);
    ArgumentUtil.ensureNotNull("value", value);
    createVariableRequest.name(name).value(value);
    return this;
  }

  @Override
  public GloballyScopedClusterVariableCreationCommandStep1 requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateClusterVariableResponse> send() {
    final HttpCamundaFuture<CreateClusterVariableResponse> result = new HttpCamundaFuture<>();
    final CreateClusterVariableResponseImpl response = new CreateClusterVariableResponseImpl();
    final String path = "/cluster-variables/global";
    httpClient.post(
        path,
        jsonMapper.toJson(createVariableRequest),
        httpRequestConfig.build(),
        ClusterVariableResult.class,
        response::setResponse,
        result);
    return result;
  }
}
