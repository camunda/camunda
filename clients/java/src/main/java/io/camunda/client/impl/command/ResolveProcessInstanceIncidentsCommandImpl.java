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
import io.camunda.client.api.command.ResolveProcessInstanceIncidentsCommandStep1;
import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateBatchOperationResponseImpl;
import io.camunda.client.protocol.rest.BatchOperationCreatedResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class ResolveProcessInstanceIncidentsCommandImpl
    implements ResolveProcessInstanceIncidentsCommandStep1 {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long processInstanceKey;

  public ResolveProcessInstanceIncidentsCommandImpl(
      final long processInstanceKey, final Duration requestTimeout, final HttpClient httpClient) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    requestTimeout(requestTimeout);
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public FinalCommandStep<CreateBatchOperationResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateBatchOperationResponse> send() {
    final HttpCamundaFuture<CreateBatchOperationResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/process-instances/" + processInstanceKey + "/incident-resolution",
        "", // sends an empty JSON request, so content type is correctly set
        httpRequestConfig.build(),
        BatchOperationCreatedResult.class,
        CreateBatchOperationResponseImpl::new,
        result);
    return result;
  }
}
