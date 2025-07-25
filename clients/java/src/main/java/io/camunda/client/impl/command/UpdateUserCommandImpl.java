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
import io.camunda.client.api.command.UpdateUserCommandStep1;
import io.camunda.client.api.response.UpdateUserResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.UpdateUserResponseImpl;
import io.camunda.client.protocol.rest.UserUpdateRequest;
import io.camunda.client.protocol.rest.UserUpdateResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class UpdateUserCommandImpl implements UpdateUserCommandStep1 {

  private final String username;
  private final UserUpdateRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public UpdateUserCommandImpl(
      final HttpClient httpClient, final String username, final JsonMapper jsonMapper) {
    this.username = username;
    request = new UserUpdateRequest();
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public UpdateUserCommandStep1 name(final String name) {
    request.setName(name);
    return this;
  }

  @Override
  public UpdateUserCommandStep1 email(final String email) {
    request.setEmail(email);
    return this;
  }

  @Override
  public UpdateUserCommandStep1 password(final String password) {
    request.setPassword(password);
    return this;
  }

  @Override
  public FinalCommandStep<UpdateUserResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UpdateUserResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("name", request.getName());
    ArgumentUtil.ensureNotNullNorEmpty("email", request.getEmail());
    final HttpCamundaFuture<UpdateUserResponse> result = new HttpCamundaFuture<>();
    final UpdateUserResponseImpl response = new UpdateUserResponseImpl();

    httpClient.put(
        "/users/" + username,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        UserUpdateResult.class,
        response::setResponse,
        result);
    return result;
  }
}
