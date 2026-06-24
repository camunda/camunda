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
import io.camunda.client.api.command.CreateGlobalTaskListenerCommandStep1;
import io.camunda.client.api.command.CreateGlobalTaskListenerCommandStep1.CreateGlobalTaskListenerCommandStep2;
import io.camunda.client.api.command.CreateGlobalTaskListenerCommandStep1.CreateGlobalTaskListenerCommandStep3;
import io.camunda.client.api.command.CreateGlobalTaskListenerCommandStep1.CreateGlobalTaskListenerCommandStep4;
import io.camunda.client.api.response.GlobalTaskListenerResponse;
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.GlobalTaskListenerResponseImpl;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.CreateGlobalTaskListenerRequest;
import io.camunda.client.protocol.rest.GlobalTaskListenerEventTypeEnum;
import io.camunda.client.protocol.rest.GlobalTaskListenerResult;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateGlobalTaskListenerCommandImpl
    implements CreateGlobalTaskListenerCommandStep1,
        CreateGlobalTaskListenerCommandStep2,
        CreateGlobalTaskListenerCommandStep3,
        CreateGlobalTaskListenerCommandStep4 {

  private final CreateGlobalTaskListenerRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateGlobalTaskListenerCommandImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new CreateGlobalTaskListenerRequest();
  }

  @Override
  public CreateGlobalTaskListenerCommandStep2 id(final String id) {
    ArgumentUtil.ensureNotNullNorEmpty("id", id);
    request.id(id);
    return this;
  }

  @Override
  public CreateGlobalTaskListenerCommandStep3 type(final String type) {
    ArgumentUtil.ensureNotNullNorEmpty("type", type);
    request.type(type);
    return this;
  }

  @Override
  public CreateGlobalTaskListenerCommandStep4 eventTypes(
      final List<GlobalTaskListenerEventType> eventTypes) {
    ArgumentUtil.ensureNotNullOrEmpty("eventTypes", eventTypes);
    eventTypes.forEach(this::eventType);
    return this;
  }

  @Override
  public CreateGlobalTaskListenerCommandStep4 eventTypes(
      final GlobalTaskListenerEventType... eventTypes) {
    eventTypes(Arrays.asList(eventTypes));
    return this;
  }

  @Override
  public CreateGlobalTaskListenerCommandStep4 eventType(
      final GlobalTaskListenerEventType eventType) {
    ArgumentUtil.ensureNotNull("eventType", eventType);
    request.addEventTypesItem(EnumUtil.convert(eventType, GlobalTaskListenerEventTypeEnum.class));
    return this;
  }

  @Override
  public CreateGlobalTaskListenerCommandStep4 retries(final Integer retries) {
    request.setRetries(retries);
    return this;
  }

  @Override
  public CreateGlobalTaskListenerCommandStep4 afterNonGlobal(final Boolean afterNonGlobal) {
    request.setAfterNonGlobal(afterNonGlobal);
    return this;
  }

  @Override
  public CreateGlobalTaskListenerCommandStep4 beforeNonGlobal() {
    return afterNonGlobal(false);
  }

  @Override
  public CreateGlobalTaskListenerCommandStep4 afterNonGlobal() {
    return afterNonGlobal(true);
  }

  @Override
  public CreateGlobalTaskListenerCommandStep4 priority(final Integer priority) {
    request.setPriority(priority);
    return this;
  }

  @Override
  public CreateGlobalTaskListenerCommandStep4 requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<GlobalTaskListenerResponse> send() {
    final HttpCamundaFuture<GlobalTaskListenerResponse> result = new HttpCamundaFuture<>();
    final GlobalTaskListenerResponseImpl response = new GlobalTaskListenerResponseImpl();
    final String path = "/global-task-listeners";
    httpClient.post(
        path,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        GlobalTaskListenerResult.class,
        response::setResponse,
        result);
    return result;
  }
}
