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

import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CreateDocumentCommandStep1;
import io.camunda.zeebe.client.api.command.CreateDocumentCommandStep1.CreateDocumentCommandStep2;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.DocumentReferenceResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.response.DocumentReferenceResponseImpl;
import io.camunda.zeebe.client.impl.util.DocumentBuilder;
import io.camunda.zeebe.client.protocol.rest.DocumentReferenceResult;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.apache.hc.core5.http.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateDocumentCommandImpl extends DocumentBuilder
    implements CreateDocumentCommandStep1, CreateDocumentCommandStep2 {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateDocumentCommandImpl.class);

  private String documentId;
  private String storeId;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateDocumentCommandImpl(
      final JsonMapper jsonMapper,
      final HttpClient httpClient,
      final ZeebeClientConfiguration configuration) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    requestTimeout(configuration.getDefaultRequestTimeout());
  }

  @Override
  public FinalCommandStep<DocumentReferenceResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(
        requestTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<DocumentReferenceResponse> send() {
    try {
      final MultipartEntityBuilder entityBuilder =
          MultipartEntityBuilder.create().setContentType(ContentType.MULTIPART_FORM_DATA);

      final String name = getMetadata().getFileName(); // can be null but is handled by the gateway
      entityBuilder.addBinaryBody("file", getContent(), ContentType.DEFAULT_BINARY, name);

      final String metadataString = jsonMapper.toJson(getMetadata());
      entityBuilder.addPart(
          "metadata", new StringBody(metadataString, ContentType.APPLICATION_JSON));

      final HttpZeebeFuture<DocumentReferenceResponse> result = new HttpZeebeFuture<>();

      final Map<String, String> queryParams = new HashMap<>();
      if (documentId != null) {
        queryParams.put("documentId", documentId);
      }
      if (storeId != null) {
        queryParams.put("storeId", storeId);
      }
      httpClient.postMultipart(
          "/documents",
          queryParams,
          entityBuilder,
          httpRequestConfig.build(),
          DocumentReferenceResult.class,
          DocumentReferenceResponseImpl::new,
          result);
      return result;
    } finally {
      try {
        getContent().close();
      } catch (final Exception e) {
        // log but otherwise ignore
        LOGGER.warn("Failed to close content stream", e);
      }
    }
  }

  @Override
  public CreateDocumentCommandStep2 content(final InputStream content) {
    super.content(content);
    return this;
  }

  @Override
  public CreateDocumentCommandStep2 content(final byte[] content) {
    super.content(content);
    return this;
  }

  @Override
  public CreateDocumentCommandStep2 content(final String content) {
    super.content(content);
    return this;
  }

  @Override
  public CreateDocumentCommandStep2 contentType(final String contentType) {
    super.contentType(contentType);
    return this;
  }

  @Override
  public CreateDocumentCommandStep2 fileName(final String name) {
    super.fileName(name);
    return this;
  }

  @Override
  public CreateDocumentCommandStep2 timeToLive(final Duration timeToLive) {
    super.timeToLive(timeToLive);
    return this;
  }

  @Override
  public CreateDocumentCommandStep2 customMetadata(final String key, final Object value) {
    super.customMetadata(key, value);
    return this;
  }

  @Override
  public CreateDocumentCommandStep2 customMetadata(final Map<String, Object> customMetadata) {
    super.customMetadata(customMetadata);
    return this;
  }

  @Override
  public CreateDocumentCommandStep2 documentId(final String documentId) {
    this.documentId = documentId;
    return this;
  }

  @Override
  public CreateDocumentCommandStep2 storeId(final String storeId) {
    this.storeId = storeId;
    return this;
  }

  @Override
  public CreateDocumentCommandStep2 processDefinitionId(final String processDefinitionId) {
    super.getMetadata().setProcessDefinitionId(processDefinitionId);
    return this;
  }

  @Override
  public CreateDocumentCommandStep2 processInstanceKey(final long processInstanceKey) {
    super.getMetadata().setProcessInstanceKey(processInstanceKey);
    return this;
  }
}
