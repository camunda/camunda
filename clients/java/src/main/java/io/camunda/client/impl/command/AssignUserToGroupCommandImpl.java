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
import io.camunda.client.api.command.AssignUserToGroupCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.AssignUserToGroupResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class AssignUserToGroupCommandImpl implements AssignUserToGroupCommandStep1 {

  private final long groupKey;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private long userKey;

  public AssignUserToGroupCommandImpl(final long groupKey, final HttpClient httpClient) {
    this.groupKey = groupKey;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public AssignUserToGroupCommandStep1 userKey(final long userKey) {
    ArgumentUtil.ensureNotNull("userKey", userKey);
    this.userKey = userKey;
    return this;
  }

  @Override
  public FinalCommandStep<AssignUserToGroupResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AssignUserToGroupResponse> send() {
    ArgumentUtil.ensureNotNull("userKey", userKey);
    final HttpCamundaFuture<AssignUserToGroupResponse> result = new HttpCamundaFuture<>();
    httpClient.put(
        "/groups/" + groupKey + "/users/" + userKey, null, httpRequestConfig.build(), result);
    return result;
  }
}
