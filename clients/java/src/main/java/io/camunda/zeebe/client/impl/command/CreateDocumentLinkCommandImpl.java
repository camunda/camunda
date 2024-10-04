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
package io.camunda.zeebe.client.impl.command;

import static io.camunda.zeebe.client.impl.command.ArgumentUtil.ensureNotNull;

import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CreateDocumentLinkCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.DocumentLinkResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.response.DocumentLinkResponseImpl;
import io.camunda.zeebe.client.protocol.rest.DocumentLink;
import io.camunda.zeebe.client.protocol.rest.DocumentLinkRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateDocumentLinkCommandImpl implements CreateDocumentLinkCommandStep1 {

  private final String documentId;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final JsonMapper jsonMapper;

  private Duration timeToLive;
  private String storeId;

  public CreateDocumentLinkCommandImpl(
      final String documentId,
      final String storeId,
      final JsonMapper jsonMapper,
      final HttpClient httpClient,
      final ZeebeClientConfiguration configuration) {
    ensureNotNull("documentId", documentId);
    this.documentId = documentId;
    this.storeId = storeId;
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    requestTimeout(configuration.getDefaultRequestTimeout());
  }

  @Override
  public CreateDocumentLinkCommandStep1 storeId(final String storeId) {
    this.storeId = storeId;
    return this;
  }

  @Override
  public CreateDocumentLinkCommandStep1 timeToLive(final Duration timeToLive) {
    this.timeToLive = timeToLive;
    return this;
  }

  @Override
  public FinalCommandStep<DocumentLinkResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(
        requestTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<DocumentLinkResponse> send() {
    final DocumentLinkRequest documentLinkRequest = new DocumentLinkRequest();
    if (timeToLive != null) {
      documentLinkRequest.setTimeToLive(timeToLive.toMillis());
    }

    final Map<String, String> queryParams = new HashMap<>();
    if (storeId != null) {
      queryParams.put("storeId", storeId);
    }
    final HttpZeebeFuture<DocumentLinkResponse> result = new HttpZeebeFuture<>();
    httpClient.post(
        String.format("/documents/%s/links", documentId),
        queryParams,
        jsonMapper.toJson(documentLinkRequest),
        httpRequestConfig.build(),
        DocumentLink.class,
        DocumentLinkResponseImpl::new,
        result);
    return result;
  }
}
