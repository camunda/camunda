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
import io.camunda.client.api.command.AssignUserTaskCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.AssignUserTaskResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.EmptyApiResponse;
import io.camunda.client.protocol.rest.UserTaskAssignmentRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class AssignUserTaskCommandImpl implements AssignUserTaskCommandStep1 {

  private final long userTaskKey;
  private final UserTaskAssignmentRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public AssignUserTaskCommandImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long userTaskKey) {
    this.jsonMapper = jsonMapper;
    this.userTaskKey = userTaskKey;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new UserTaskAssignmentRequest();
  }

  @Override
  public FinalCommandStep<AssignUserTaskResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AssignUserTaskResponse> send() {
    final HttpCamundaFuture<AssignUserTaskResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/user-tasks/" + userTaskKey + "/assignment",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        r -> new EmptyApiResponse(),
        result);
    return result;
  }

  @Override
  public AssignUserTaskCommandStep1 action(final String action) {
    request.setAction(action);
    return this;
  }

  @Override
  public AssignUserTaskCommandStep1 assignee(final String assignee) {
    ArgumentUtil.ensureNotNull("assignee", assignee);
    request.setAssignee(assignee);
    return this;
  }

  @Override
  public AssignUserTaskCommandStep1 allowOverride(final boolean allowOverride) {
    request.setAllowOverride(allowOverride);
    return this;
  }
}
