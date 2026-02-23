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
import io.camunda.client.api.command.UpdateGlobalTaskListenerCommandStep1;
import io.camunda.client.api.command.UpdateGlobalTaskListenerCommandStep1.UpdateGlobalTaskListenerCommandStep2;
import io.camunda.client.api.command.UpdateGlobalTaskListenerCommandStep1.UpdateGlobalTaskListenerCommandStep3;
import io.camunda.client.api.response.GlobalTaskListenerResponse;
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.GlobalTaskListenerResponseImpl;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.GlobalTaskListenerEventTypeEnum;
import io.camunda.client.protocol.rest.GlobalTaskListenerResult;
import io.camunda.client.protocol.rest.UpdateGlobalTaskListenerRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class UpdateGlobalTaskListenerCommandImpl
    implements UpdateGlobalTaskListenerCommandStep1,
        UpdateGlobalTaskListenerCommandStep2,
        UpdateGlobalTaskListenerCommandStep3 {

  private final String listenerId;
  private final UpdateGlobalTaskListenerRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public UpdateGlobalTaskListenerCommandImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String id) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new UpdateGlobalTaskListenerRequest();
    ArgumentUtil.ensureNotNullNorEmpty("id", id);
    listenerId = id;
  }

  @Override
  public UpdateGlobalTaskListenerCommandStep2 type(final String type) {
    ArgumentUtil.ensureNotNullNorEmpty("type", type);
    request.type(type);
    return this;
  }

  @Override
  public UpdateGlobalTaskListenerCommandStep3 eventTypes(
      final List<GlobalTaskListenerEventType> eventTypes) {
    ArgumentUtil.ensureNotNullOrEmpty("eventTypes", eventTypes);
    eventTypes.forEach(this::eventType);
    return this;
  }

  @Override
  public UpdateGlobalTaskListenerCommandStep3 eventTypes(
      final GlobalTaskListenerEventType... eventTypes) {
    eventTypes(Arrays.asList(eventTypes));
    return this;
  }

  @Override
  public UpdateGlobalTaskListenerCommandStep3 eventType(
      final GlobalTaskListenerEventType eventType) {
    ArgumentUtil.ensureNotNull("eventType", eventType);
    request.addEventTypesItem(EnumUtil.convert(eventType, GlobalTaskListenerEventTypeEnum.class));
    return this;
  }

  @Override
  public UpdateGlobalTaskListenerCommandStep3 retries(final Integer retries) {
    request.setRetries(retries);
    return this;
  }

  @Override
  public UpdateGlobalTaskListenerCommandStep3 afterNonGlobal(final Boolean afterNonGlobal) {
    request.setAfterNonGlobal(afterNonGlobal);
    return this;
  }

  @Override
  public UpdateGlobalTaskListenerCommandStep3 beforeNonGlobal() {
    return afterNonGlobal(false);
  }

  @Override
  public UpdateGlobalTaskListenerCommandStep3 afterNonGlobal() {
    return afterNonGlobal(true);
  }

  @Override
  public UpdateGlobalTaskListenerCommandStep3 priority(final Integer priority) {
    request.setPriority(priority);
    return this;
  }

  @Override
  public UpdateGlobalTaskListenerCommandStep3 requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<GlobalTaskListenerResponse> send() {
    final HttpCamundaFuture<GlobalTaskListenerResponse> result = new HttpCamundaFuture<>();
    final GlobalTaskListenerResponseImpl response = new GlobalTaskListenerResponseImpl();
    final String path = "/global-task-listeners/" + listenerId;
    httpClient.put(
        path,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        GlobalTaskListenerResult.class,
        response::setResponse,
        result);
    return result;
  }
}
