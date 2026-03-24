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
import io.camunda.client.api.command.DeleteGlobalExecutionListenerCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.DeleteGlobalExecutionListenerResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.DeleteGlobalExecutionListenerResponseImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class DeleteGlobalExecutionListenerCommandImpl
    implements DeleteGlobalExecutionListenerCommandStep1 {

  private final String listenerId;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public DeleteGlobalExecutionListenerCommandImpl(
      final HttpClient httpClient, final String listenerId) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    ArgumentUtil.ensureNotNullNorEmpty("id", listenerId);
    this.listenerId = listenerId;
  }

  @Override
  public FinalCommandStep<DeleteGlobalExecutionListenerResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<DeleteGlobalExecutionListenerResponse> send() {
    final HttpCamundaFuture<DeleteGlobalExecutionListenerResponse> result =
        new HttpCamundaFuture<>();
    httpClient.delete(
        "/global-execution-listeners/" + listenerId,
        httpRequestConfig.build(),
        DeleteGlobalExecutionListenerResponseImpl::new,
        result);
    return result;
  }
}
