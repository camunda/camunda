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
import io.camunda.client.api.command.CreateGlobalExecutionListenerCommandStep1;
import io.camunda.client.api.command.CreateGlobalExecutionListenerCommandStep1.CreateGlobalExecutionListenerCommandStep2;
import io.camunda.client.api.command.CreateGlobalExecutionListenerCommandStep1.CreateGlobalExecutionListenerCommandStep3;
import io.camunda.client.api.response.GlobalExecutionListenerResponse;
import io.camunda.client.api.search.enums.GlobalExecutionListenerCategory;
import io.camunda.client.api.search.enums.GlobalExecutionListenerElementType;
import io.camunda.client.api.search.enums.GlobalExecutionListenerEventType;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.GlobalExecutionListenerResponseImpl;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.CreateGlobalExecutionListenerRequest;
import io.camunda.client.protocol.rest.GlobalExecutionListenerCategoryEnum;
import io.camunda.client.protocol.rest.GlobalExecutionListenerElementTypeEnum;
import io.camunda.client.protocol.rest.GlobalExecutionListenerEventTypeEnum;
import io.camunda.client.protocol.rest.GlobalExecutionListenerResult;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateGlobalExecutionListenerCommandImpl
    implements CreateGlobalExecutionListenerCommandStep1,
        CreateGlobalExecutionListenerCommandStep2,
        CreateGlobalExecutionListenerCommandStep3 {

  private final CreateGlobalExecutionListenerRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateGlobalExecutionListenerCommandImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String listenerId) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new CreateGlobalExecutionListenerRequest();
    ArgumentUtil.ensureNotNullNorEmpty("id", listenerId);
    request.setId(listenerId);
  }

  @Override
  public CreateGlobalExecutionListenerCommandStep2 type(final String type) {
    ArgumentUtil.ensureNotNullNorEmpty("type", type);
    request.type(type);
    return this;
  }

  @Override
  public CreateGlobalExecutionListenerCommandStep3 eventTypes(
      final List<GlobalExecutionListenerEventType> eventTypes) {
    ArgumentUtil.ensureNotNullOrEmpty("eventTypes", eventTypes);
    eventTypes.forEach(this::eventType);
    return this;
  }

  @Override
  public CreateGlobalExecutionListenerCommandStep3 eventTypes(
      final GlobalExecutionListenerEventType... eventTypes) {
    eventTypes(Arrays.asList(eventTypes));
    return this;
  }

  @Override
  public CreateGlobalExecutionListenerCommandStep3 eventType(
      final GlobalExecutionListenerEventType eventType) {
    ArgumentUtil.ensureNotNull("eventType", eventType);
    request.addEventTypesItem(
        EnumUtil.convert(eventType, GlobalExecutionListenerEventTypeEnum.class));
    return this;
  }

  @Override
  public CreateGlobalExecutionListenerCommandStep3 retries(final int retries) {
    request.setRetries(retries);
    return this;
  }

  @Override
  public CreateGlobalExecutionListenerCommandStep3 afterNonGlobal(final boolean afterNonGlobal) {
    request.setAfterNonGlobal(afterNonGlobal);
    return this;
  }

  @Override
  public CreateGlobalExecutionListenerCommandStep3 priority(final int priority) {
    request.setPriority(priority);
    return this;
  }

  @Override
  public CreateGlobalExecutionListenerCommandStep3 elementTypes(
      final List<GlobalExecutionListenerElementType> elementTypes) {
    if (elementTypes != null) {
      elementTypes.forEach(
          et ->
              request.addElementTypesItem(
                  EnumUtil.convert(et, GlobalExecutionListenerElementTypeEnum.class)));
    }
    return this;
  }

  @Override
  public CreateGlobalExecutionListenerCommandStep3 elementTypes(
      final GlobalExecutionListenerElementType... elementTypes) {
    return elementTypes(Arrays.asList(elementTypes));
  }

  @Override
  public CreateGlobalExecutionListenerCommandStep3 categories(
      final List<GlobalExecutionListenerCategory> categories) {
    if (categories != null) {
      categories.forEach(
          c ->
              request.addCategoriesItem(
                  EnumUtil.convert(c, GlobalExecutionListenerCategoryEnum.class)));
    }
    return this;
  }

  @Override
  public CreateGlobalExecutionListenerCommandStep3 categories(
      final GlobalExecutionListenerCategory... categories) {
    return categories(Arrays.asList(categories));
  }

  @Override
  public CreateGlobalExecutionListenerCommandStep3 requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<GlobalExecutionListenerResponse> send() {
    final HttpCamundaFuture<GlobalExecutionListenerResponse> result = new HttpCamundaFuture<>();
    final GlobalExecutionListenerResponseImpl response = new GlobalExecutionListenerResponseImpl();
    final String path = "/global-execution-listeners";
    httpClient.post(
        path,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        GlobalExecutionListenerResult.class,
        response::setResponse,
        result);
    return result;
  }
}
