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
package io.camunda.zeebe.client.impl.fetch;

import static io.camunda.zeebe.client.impl.command.ArgumentUtil.ensureNotNull;

import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.fetch.DocumentContentGetRequest;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.hc.client5.http.config.RequestConfig;

public class DocumentContentGetRequestImpl implements DocumentContentGetRequest {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final String documentId;
  private String storeId;
  private String contentHash;

  public DocumentContentGetRequestImpl(
      final HttpClient httpClient,
      final String documentId,
      final String storeId,
      final String contentHash,
      final ZeebeClientConfiguration configuration) {
    ensureNotNull("documentId", documentId);
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    this.documentId = documentId;
    this.storeId = storeId;
    this.contentHash = contentHash;
    requestTimeout(configuration.getDefaultRequestTimeout());
  }

  @Override
  public FinalCommandStep<InputStream> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(
        requestTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<InputStream> send() {
    final HttpZeebeFuture<InputStream> result = new HttpZeebeFuture<>();
    final Map<String, String> queryParams = new HashMap<>();
    if (storeId != null) {
      queryParams.put("storeId", storeId);
    }
    queryParams.put("contentHash", contentHash);
    httpClient.get(
        String.format("/documents/%s", documentId),
        queryParams,
        httpRequestConfig.build(),
        InputStream.class,
        is -> is,
        result);
    return result;
  }

  @Override
  public DocumentContentGetRequest storeId(final String storeId) {
    this.storeId = storeId;
    return this;
  }

  @Override
  public DocumentContentGetRequest contentHash(final String contentHash) {
    this.contentHash = contentHash;
    return this;
  }
}
