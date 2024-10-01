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
package io.camunda.zeebe.client.impl.fetch;

import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.fetch.UserTaskGetRequest;
import io.camunda.zeebe.client.api.search.response.UserTask;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.search.response.UserTaskImpl;
import io.camunda.zeebe.client.protocol.rest.UserTaskItem;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class UserTaskGetRequestImpl implements UserTaskGetRequest {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long userTaskKey;

  public UserTaskGetRequestImpl(final HttpClient httpClient, final long userTaskKey) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    this.userTaskKey = userTaskKey;
  }

  @Override
  public UserTaskGetRequest requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<UserTask> send() {
    final HttpZeebeFuture<UserTask> result = new HttpZeebeFuture<>();
    httpClient.get(
        String.format("/user-tasks/%d", userTaskKey),
        httpRequestConfig.build(),
        UserTaskItem.class,
        UserTaskImpl::new,
        result);
    return result;
  }
}
