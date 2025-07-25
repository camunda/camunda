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
import io.camunda.client.api.command.CreateUserCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.CreateUserResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateUserResponseImpl;
import io.camunda.client.protocol.rest.UserCreateResult;
import io.camunda.client.protocol.rest.UserRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class CreateUserCommandImpl implements CreateUserCommandStep1 {

  private final UserRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateUserCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new UserRequest();
  }

  @Override
  public FinalCommandStep<CreateUserResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateUserResponse> send() {
    ArgumentUtil.ensureNotNull("username", request.getUsername());
    ArgumentUtil.ensureNotNull("email", request.getEmail());
    ArgumentUtil.ensureNotNull("name", request.getName());
    ArgumentUtil.ensureNotNull("password", request.getPassword());
    final HttpCamundaFuture<CreateUserResponse> result = new HttpCamundaFuture<>();
    final CreateUserResponseImpl response = new CreateUserResponseImpl();
    httpClient.post(
        "/users",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        UserCreateResult.class,
        response::setResponse,
        result);
    return result;
  }

  @Override
  public CreateUserCommandStep1 username(final String username) {
    ArgumentUtil.ensureNotNull("username", username);
    request.setUsername(username);
    return this;
  }

  @Override
  public CreateUserCommandStep1 email(final String email) {
    ArgumentUtil.ensureNotNull("email", email);
    request.setEmail(email);
    return this;
  }

  @Override
  public CreateUserCommandStep1 name(final String name) {
    ArgumentUtil.ensureNotNull("name", name);
    request.setName(name);
    return this;
  }

  @Override
  public CreateUserCommandStep1 password(final String password) {
    ArgumentUtil.ensureNotNull("password", password);
    request.setPassword(password);
    return this;
  }
}
