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
import io.camunda.client.api.command.CancelBatchOperationStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.CancelBatchOperationResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.EmptyApiResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

/** Implementation of the command to cancel a batch operation. */
public final class CancelBatchOperationCommandImpl implements CancelBatchOperationStep1 {

  private final String batchOperationKey;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CancelBatchOperationCommandImpl(
      final HttpClient httpClient, final String batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalCommandStep<CancelBatchOperationResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CancelBatchOperationResponse> send() {
    final HttpCamundaFuture<CancelBatchOperationResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/batch-operations/" + batchOperationKey + "/cancellation",
        null,
        httpRequestConfig.build(),
        r -> new EmptyApiResponse(),
        result);
    return result;
  }
}
