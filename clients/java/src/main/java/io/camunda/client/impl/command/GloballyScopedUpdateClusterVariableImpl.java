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
import io.camunda.client.api.command.GloballyScopedClusterVariableUpdateCommandStep1;
import io.camunda.client.api.response.UpdateClusterVariableResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.UpdateClusterVariableResponseImpl;
import io.camunda.client.protocol.rest.ClusterVariableResult;
import io.camunda.client.protocol.rest.UpdateClusterVariableRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class GloballyScopedUpdateClusterVariableImpl
    implements GloballyScopedClusterVariableUpdateCommandStep1 {

  private final UpdateClusterVariableRequest updateVariableRequest;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private String name;

  public GloballyScopedUpdateClusterVariableImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    updateVariableRequest = new UpdateClusterVariableRequest();
  }

  @Override
  public GloballyScopedClusterVariableUpdateCommandStep1 update(
      final String name, final Object value) {
    ArgumentUtil.ensureNotNullNorEmpty("name", name);
    ArgumentUtil.ensureNotNull("value", value);
    this.name = name;
    updateVariableRequest.value(value);
    return this;
  }

  @Override
  public GloballyScopedClusterVariableUpdateCommandStep1 requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UpdateClusterVariableResponse> send() {
    final HttpCamundaFuture<UpdateClusterVariableResponse> result = new HttpCamundaFuture<>();
    final UpdateClusterVariableResponseImpl response = new UpdateClusterVariableResponseImpl();
    final String path = "/cluster-variables/global/" + name;
    httpClient.put(
        path,
        jsonMapper.toJson(updateVariableRequest),
        httpRequestConfig.build(),
        ClusterVariableResult.class,
        response::setResponse,
        result);
    return result;
  }
}
