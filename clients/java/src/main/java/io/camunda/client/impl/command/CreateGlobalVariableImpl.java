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
import io.camunda.client.api.command.GlobalVariableCreationRequestStep1;
import io.camunda.client.api.response.CreateGlobalVariableResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateGlobalVariableResponseImpl;
import io.camunda.client.protocol.rest.GlobalVariableCreateQuery;
import io.camunda.client.protocol.rest.GlobalVariableCreateResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateGlobalVariableImpl implements GlobalVariableCreationRequestStep1 {

  private final GlobalVariableCreateQuery globalVariableCreateQuery;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateGlobalVariableImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    globalVariableCreateQuery = new GlobalVariableCreateQuery();
  }

  @Override
  public FinalCommandStep<CreateGlobalVariableResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateGlobalVariableResponse> send() {
    final CreateGlobalVariableResponseImpl response = new CreateGlobalVariableResponseImpl();
    final HttpCamundaFuture<CreateGlobalVariableResponse> result = new HttpCamundaFuture<>();
    httpClient.put(
        "/variables/create",
        jsonMapper.toJson(globalVariableCreateQuery),
        httpRequestConfig.build(),
        GlobalVariableCreateResult.class,
        response::setResponse,
        result);
    return result;
  }

  @Override
  public GlobalVariableCreationRequestStep1 variable(final String key, final Object value) {
    globalVariableCreateQuery.setKey(key);
    globalVariableCreateQuery.setValue(value);
    return this;
  }
}
