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
import io.camunda.client.api.command.LookupUserTaskCommandStep1;
import io.camunda.client.api.response.LookupUserTaskResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.LookupUserTaskResponseImpl;
import io.camunda.client.protocol.rest.UserTaskLookupRequest;
import io.camunda.client.protocol.rest.UserTaskLookupResult;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class LookupUserTaskCommandImpl implements LookupUserTaskCommandStep1 {

  private final long userTaskKey;
  private final UserTaskLookupRequest request;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public LookupUserTaskCommandImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long userTaskKey) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.userTaskKey = userTaskKey;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new UserTaskLookupRequest();
  }

  @Override
  public LookupUserTaskCommandStep1 fetchVariables(final List<String> fetchVariables) {
    request.setFetchVariables(fetchVariables);
    return this;
  }

  @Override
  public LookupUserTaskCommandStep1 fetchVariables(final String... fetchVariables) {
    return fetchVariables(Arrays.asList(fetchVariables));
  }

  @Override
  public FinalCommandStep<LookupUserTaskResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<LookupUserTaskResponse> send() {
    final HttpCamundaFuture<LookupUserTaskResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/user-tasks/" + userTaskKey + "/lookup",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        UserTaskLookupResult.class,
        response -> new LookupUserTaskResponseImpl(jsonMapper, response),
        result);
    return result;
  }
}
