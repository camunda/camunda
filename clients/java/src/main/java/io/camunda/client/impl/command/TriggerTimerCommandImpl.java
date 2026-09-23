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
import io.camunda.client.api.command.TriggerTimerCommandStep1;
import io.camunda.client.api.command.TriggerTimerCommandStep1.TriggerTimerCommandStep2;
import io.camunda.client.api.response.TriggerTimerResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.TriggerTimerResponseImpl;
import io.camunda.client.protocol.rest.TimerTriggerRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class TriggerTimerCommandImpl implements TriggerTimerCommandStep1, TriggerTimerCommandStep2 {

  private final TimerTriggerRequest httpRequestObject = new TimerTriggerRequest();
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long processInstanceKey;
  private final JsonMapper jsonMapper;

  public TriggerTimerCommandImpl(
      final long processInstanceKey, final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.processInstanceKey = processInstanceKey;
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public TriggerTimerCommandStep2 elementId(final String elementId) {
    ArgumentUtil.ensureNotNullNorEmpty("elementId", elementId);
    httpRequestObject.setElementId(elementId);
    return this;
  }

  @Override
  public FinalCommandStep<TriggerTimerResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<TriggerTimerResponse> send() {
    final HttpCamundaFuture<TriggerTimerResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/process-instances/" + processInstanceKey + "/timers/trigger",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        TriggerTimerResponseImpl::new,
        result);
    return result;
  }
}
