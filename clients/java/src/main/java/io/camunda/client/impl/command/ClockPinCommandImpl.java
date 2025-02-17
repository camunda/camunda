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
import io.camunda.client.api.command.ClockPinCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.PinClockResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.protocol.rest.ClockPinRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class ClockPinCommandImpl implements ClockPinCommandStep1 {

  private final ClockPinRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ClockPinCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new ClockPinRequest();
  }

  @Override
  public ClockPinCommandStep1 time(final long timestamp) {
    ArgumentUtil.ensureNotNegative("timestamp", timestamp);
    request.setTimestamp(timestamp);
    return this;
  }

  @Override
  public ClockPinCommandStep1 time(final Instant instant) {
    ArgumentUtil.ensureNotNull("instant", instant);
    ArgumentUtil.ensureNotBefore("instant", instant, Instant.EPOCH);
    return time(instant.toEpochMilli());
  }

  @Override
  public FinalCommandStep<PinClockResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<PinClockResponse> send() {
    final HttpCamundaFuture<PinClockResponse> result = new HttpCamundaFuture<>();
    httpClient.put("/clock", jsonMapper.toJson(request), httpRequestConfig.build(), result);
    return result;
  }
}
