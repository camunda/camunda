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
import io.camunda.client.api.command.CreateGroupCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.CreateGroupResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateGroupResponseImpl;
import io.camunda.client.protocol.rest.GroupCreateRequest;
import io.camunda.client.protocol.rest.GroupCreateResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateGroupCommandImpl implements CreateGroupCommandStep1 {

  private final GroupCreateRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateGroupCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new GroupCreateRequest();
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public CreateGroupCommandStep1 name(final String name) {
    request.name(name);
    return this;
  }

  @Override
  public FinalCommandStep<CreateGroupResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateGroupResponse> send() {
    ArgumentUtil.ensureNotNull("name", request.getName());
    final HttpCamundaFuture<CreateGroupResponse> result = new HttpCamundaFuture<>();
    final CreateGroupResponseImpl response = new CreateGroupResponseImpl();
    httpClient.post(
        "/groups",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        GroupCreateResult.class,
        response::setResponse,
        result);
    return result;
  }
}
