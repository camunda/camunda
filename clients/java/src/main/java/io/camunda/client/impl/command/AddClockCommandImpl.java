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
import io.camunda.client.api.command.AddClockCommandStep1;
import io.camunda.client.api.command.AddClockCommandStep1.AddClockCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ClockResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.ClockResponseImpl;
import io.camunda.client.protocol.rest.ClockAddRequest;
import io.camunda.client.protocol.rest.ClockResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class AddClockCommandImpl implements AddClockCommandStep1, AddClockCommandStep2 {

  private final ClockAddRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public AddClockCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new ClockAddRequest();
  }

  @Override
  public AddClockCommandStep2 offset(final Duration offset) {
    ArgumentUtil.ensureNotNull("offset", offset);
    request.setOffset(offset.toString());
    return this;
  }

  @Override
  public FinalCommandStep<ClockResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<ClockResponse> send() {
    final HttpCamundaFuture<ClockResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/clock/add",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ClockResult.class,
        ClockResponseImpl::new,
        result);
    return result;
  }
}
