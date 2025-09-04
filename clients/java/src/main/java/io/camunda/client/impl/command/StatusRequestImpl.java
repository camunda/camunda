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
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.StatusRequestStep1;
import io.camunda.client.api.response.StatusResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.StatusResponseImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class StatusRequestImpl implements StatusRequestStep1 {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private Duration requestTimeout;

  public StatusRequestImpl(final HttpClient httpClient, final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalCommandStep<StatusResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public CamundaFuture<StatusResponse> send() {
    final HttpCamundaFuture<StatusResponse> result = new HttpCamundaFuture<>();

    final Predicate<Integer> successPredicate = status -> status == 204 || status == 503;
    httpClient.get(
        "/status",
        httpRequestConfig
            .setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .build(),
        successPredicate,
        StatusResponseImpl::new,
        result);

    return result;
  }
}
