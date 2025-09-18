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
import io.camunda.client.api.command.SuspendBatchOperationStep1;
import io.camunda.client.api.response.SuspendBatchOperationResponse;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.SuspendBatchOperationResponseImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

/** Implementation of the command to suspend a batch operation. */
public final class SuspendBatchOperationCommandImpl implements SuspendBatchOperationStep1 {

  private final String batchOperationKey;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public SuspendBatchOperationCommandImpl(
      final HttpClient httpClient, final String batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalCommandStep<SuspendBatchOperationResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SuspendBatchOperationResponse> send() {
    return httpClient.post(
        "/batch-operations/" + batchOperationKey + "/suspension",
        null,
        httpRequestConfig.build(),
        SuspendBatchOperationResponseImpl::new);
  }
}
