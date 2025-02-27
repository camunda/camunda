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
import io.camunda.client.api.command.ActivateAdHocSubprocessActivitiesCommandStep1;
import io.camunda.client.api.command.ActivateAdHocSubprocessActivitiesCommandStep1.ActivateAdHocSubprocessActivitiesCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivateAdHocSubprocessActivitiesResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.protocol.rest.AdHocSubprocessActivateActivitiesInstruction;
import io.camunda.client.protocol.rest.AdHocSubprocessActivateActivityReference;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class ActivateAdHocSubprocessActivitiesCommandImpl
    implements ActivateAdHocSubprocessActivitiesCommandStep1,
        ActivateAdHocSubprocessActivitiesCommandStep2 {

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  private final String adHocSubprocessInstanceKey;
  private final AdHocSubprocessActivateActivitiesInstruction httpRequestObject;

  public ActivateAdHocSubprocessActivitiesCommandImpl(
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final String adHocSubprocessInstanceKey) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();

    this.adHocSubprocessInstanceKey = adHocSubprocessInstanceKey;
    httpRequestObject = new AdHocSubprocessActivateActivitiesInstruction();
  }

  @Override
  public ActivateAdHocSubprocessActivitiesCommandStep2 activateElement(final String elementId) {
    httpRequestObject.addElementsItem(
        new AdHocSubprocessActivateActivityReference().elementId(elementId));
    return this;
  }

  @Override
  public FinalCommandStep<ActivateAdHocSubprocessActivitiesResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<ActivateAdHocSubprocessActivitiesResponse> send() {
    final HttpCamundaFuture<ActivateAdHocSubprocessActivitiesResponse> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/element-instances/ad-hoc-activities/" + adHocSubprocessInstanceKey + "/activate",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        result);
    return result;
  }
}
