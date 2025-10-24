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
package io.camunda.zeebe.client.impl.command;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CompleteUserTaskCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.CompleteUserTaskResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.protocol.rest.UserTaskCompletionRequest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

/**
 * This command implementation currently does not extend {@link CommandWithVariables} since we would
 * have to handle a String-ified JSON variables object. The request object itself expects a {@link
 * Map} though. In the future, we might extend this to also allow all options from {@link
 * CommandWithVariables} here.
 */
public final class CompleteUserTaskCommandImpl implements CompleteUserTaskCommandStep1 {

  private final long userTaskKey;
  private final UserTaskCompletionRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CompleteUserTaskCommandImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long userTaskKey) {
    this.jsonMapper = jsonMapper;
    this.userTaskKey = userTaskKey;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new UserTaskCompletionRequest();
  }

  @Override
  public FinalCommandStep<CompleteUserTaskResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<CompleteUserTaskResponse> send() {
    final HttpZeebeFuture<CompleteUserTaskResponse> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/user-tasks/" + userTaskKey + "/completion",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        result);
    return result;
  }

  @Override
  public CompleteUserTaskCommandStep1 action(final String action) {
    request.setAction(action);
    return this;
  }

  @Override
  public CompleteUserTaskCommandStep1 variables(final Map<String, Object> variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    request.setVariables(variables);
    return this;
  }
}
