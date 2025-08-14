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
import io.camunda.client.api.command.ActivateAdHocSubProcessActivitiesCommandStep1;
import io.camunda.client.api.command.ActivateAdHocSubProcessActivitiesCommandStep1.ActivateAdHocSubProcessActivitiesCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivateAdHocSubProcessActivitiesResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.EmptyApiResponse;
import io.camunda.client.protocol.rest.AdHocSubProcessActivateActivitiesInstruction;
import io.camunda.client.protocol.rest.AdHocSubProcessActivateActivityReference;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class ActivateAdHocSubProcessActivitiesCommandImpl
    extends CommandWithVariables<ActivateAdHocSubProcessActivitiesCommandStep2>
    implements ActivateAdHocSubProcessActivitiesCommandStep1,
        ActivateAdHocSubProcessActivitiesCommandStep2 {

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  private final String adHocSubProcessInstanceKey;
  private AdHocSubProcessActivateActivityReference latestActivateElement;
  private final AdHocSubProcessActivateActivitiesInstruction httpRequestObject;

  public ActivateAdHocSubProcessActivitiesCommandImpl(
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final String adHocSubProcessInstanceKey) {
    super(jsonMapper);
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();

    this.adHocSubProcessInstanceKey = adHocSubProcessInstanceKey;
    httpRequestObject = new AdHocSubProcessActivateActivitiesInstruction();
  }

  @Override
  public ActivateAdHocSubProcessActivitiesCommandStep2 activateElement(final String elementId) {
    latestActivateElement = new AdHocSubProcessActivateActivityReference().elementId(elementId);
    httpRequestObject.addElementsItem(latestActivateElement);
    return this;
  }

  @Override
  public ActivateAdHocSubProcessActivitiesCommandStep2 activateElement(
      final String elementId, final Map<String, Object> variables) {
    activateElement(elementId);
    latestActivateElement.setVariables(variables);
    return this;
  }

  @Override
  public ActivateAdHocSubProcessActivitiesCommandStep2 cancelRemainingInstances(
      final boolean cancelRemainingInstances) {
    httpRequestObject.cancelRemainingInstances(cancelRemainingInstances);
    return this;
  }

  @Override
  public FinalCommandStep<ActivateAdHocSubProcessActivitiesResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<ActivateAdHocSubProcessActivitiesResponse> send() {
    final HttpCamundaFuture<ActivateAdHocSubProcessActivitiesResponse> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/element-instances/ad-hoc-activities/" + adHocSubProcessInstanceKey + "/activation",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        r -> new EmptyApiResponse(),
        result);
    return result;
  }

  @Override
  protected ActivateAdHocSubProcessActivitiesCommandStep2 setVariablesInternal(
      final String variables) {
    latestActivateElement.setVariables(jsonMapper.fromJsonAsMap(variables));
    return this;
  }
}
