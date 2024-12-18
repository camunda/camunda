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

import static io.camunda.client.impl.command.ArgumentUtil.ensureNotNull;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.DeleteDocumentCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.DeleteDocumentResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.hc.client5.http.config.RequestConfig;

public class DeleteDocumentCommandImpl implements DeleteDocumentCommandStep1 {

  private final HttpClient client;
  private final String documentId;
  private final RequestConfig.Builder requestConfig;

  private String storeId;

  public DeleteDocumentCommandImpl(
      final String documentId,
      final String storeId,
      final HttpClient client,
      final CamundaClientConfiguration configuration) {
    ensureNotNull("documentId", documentId);
    this.documentId = documentId;
    this.client = client;
    this.storeId = storeId;
    requestConfig = client.newRequestConfig();
    requestTimeout(configuration.getDefaultRequestTimeout());
  }

  @Override
  public DeleteDocumentCommandStep1 storeId(final String storeId) {
    this.storeId = storeId;
    return this;
  }

  @Override
  public FinalCommandStep<DeleteDocumentResponse> requestTimeout(final Duration requestTimeout) {
    requestConfig.setResponseTimeout(
        requestTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<DeleteDocumentResponse> send() {
    final Map<String, String> queryParams = new HashMap<>();
    if (storeId != null) {
      queryParams.put("storeId", storeId);
    }
    final HttpCamundaFuture<DeleteDocumentResponse> result = new HttpCamundaFuture<>();
    client.delete(
        String.format("/documents/%s", documentId), queryParams, requestConfig.build(), result);
    return result;
  }
}
