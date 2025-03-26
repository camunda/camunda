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

import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.DocumentCreationBatchResponse;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CreateDocumentBatchCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.DocumentReferenceBatchResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.response.DocumentReferenceBatchResponseImpl;
import io.camunda.zeebe.client.impl.util.DocumentBuilder;
<<<<<<< HEAD
=======
import io.camunda.zeebe.client.impl.util.ParseUtil;
import io.camunda.zeebe.client.protocol.rest.DocumentCreationBatchResponse;
>>>>>>> 0b906ae6 (fix: adjust remaining number types to strings in the API)
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.InputStreamBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.MultipartPart;
import org.apache.hc.client5.http.entity.mime.MultipartPartBuilder;
import org.apache.hc.core5.http.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateDocumentBatchCommandImpl implements CreateDocumentBatchCommandStep1 {

  public static final String METADATA_PART_HEADER = "X-Document-Metadata";
  private static final Logger LOGGER =
      LoggerFactory.getLogger(CreateDocumentBatchCommandImpl.class);
  private final List<DocumentBuilder> documents = new ArrayList<>();
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  private final Map<String, String> queryParams = new HashMap<>();
  private String processDefinitionId;
  private Long processInstanceKey;

  public CreateDocumentBatchCommandImpl(
      final JsonMapper jsonMapper,
      final HttpClient httpClient,
      final ZeebeClientConfiguration configuration) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    requestTimeout(configuration.getDefaultRequestTimeout());
  }

  @Override
  public FinalCommandStep<DocumentReferenceBatchResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(
        requestTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<DocumentReferenceBatchResponse> send() {
    try {
      final MultipartEntityBuilder entityBuilder =
          MultipartEntityBuilder.create().setContentType(ContentType.MULTIPART_FORM_DATA);

      for (final DocumentBuilder document : documents) {
        final String fileName = document.getMetadata().getFileName();
        ensureNotNull("fileName", fileName);
        final InputStreamBody body =
            new InputStreamBody(document.getContent(), ContentType.DEFAULT_BINARY, fileName);
        if (processDefinitionId != null) {
          document.getMetadata().setProcessDefinitionId(processDefinitionId);
        }
        if (processInstanceKey != null) {
          document.getMetadata().setProcessInstanceKey(ParseUtil.keyToString(processInstanceKey));
        }
        final String metadataString = jsonMapper.toJson(document.getMetadata());
        final MultipartPart part =
            MultipartPartBuilder.create()
                .setBody(body)
                .setHeader(
                    "Content-Disposition", "form-data; name=files; filename=\"" + fileName + "\"")
                .setHeader(METADATA_PART_HEADER, metadataString)
                .build();
        entityBuilder.addPart(part);
      }

      final HttpZeebeFuture<DocumentReferenceBatchResponse> result = new HttpZeebeFuture<>();
      httpClient.postMultipart(
          "/documents/batch",
          queryParams,
          entityBuilder,
          httpRequestConfig.build(),
          DocumentCreationBatchResponse.class,
          DocumentReferenceBatchResponseImpl::new,
          result);
      return result;
    } finally {
      documents.stream()
          .map(DocumentBuilder::getContent)
          .filter(Objects::nonNull)
          .forEach(
              content -> {
                try {
                  content.close();
                } catch (final Exception e) {
                  // log but otherwise ignore
                  LOGGER.warn("Failed to close content stream", e);
                }
              });
    }
  }

  @Override
  public CreateDocumentBatchCommandStep1 storeId(final String storeId) {
    ensureNotNull("storeId", storeId);
    queryParams.put("storeId", storeId);
    return this;
  }

  @Override
  public CreateDocumentBatchCommandStep1 processDefinitionId(final String processDefinitionId) {
    ensureNotNull("processDefinitionId", processDefinitionId);
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  @Override
  public CreateDocumentBatchCommandStep1 processInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public CreateDocumentBatchCommandStep2 addDocument() {
    final DocumentBuilderStep2BatchImpl documentBuilder = new DocumentBuilderStep2BatchImpl(this);
    documents.add(documentBuilder);
    return documentBuilder;
  }

  public static class DocumentBuilderStep2BatchImpl extends DocumentBuilder
      implements CreateDocumentBatchCommandStep2 {

    private final CreateDocumentBatchCommandImpl parentStep;

    public DocumentBuilderStep2BatchImpl(final CreateDocumentBatchCommandImpl parentStep) {
      super();
      this.parentStep = parentStep;
    }

    @Override
    public CreateDocumentBatchCommandStep2 content(final InputStream content) {
      super.content(content);
      return this;
    }

    @Override
    public CreateDocumentBatchCommandStep2 content(final byte[] content) {
      super.content(content);
      return this;
    }

    @Override
    public CreateDocumentBatchCommandStep2 content(final String content) {
      super.content(content);
      return this;
    }

    @Override
    public CreateDocumentBatchCommandStep2 contentType(final String contentType) {
      super.contentType(contentType);
      return this;
    }

    @Override
    public CreateDocumentBatchCommandStep2 fileName(final String name) {
      super.fileName(name);
      return this;
    }

    @Override
    public CreateDocumentBatchCommandStep2 timeToLive(final Duration timeToLive) {
      super.timeToLive(timeToLive);
      return this;
    }

    @Override
    public CreateDocumentBatchCommandStep2 customMetadata(final String key, final Object value) {
      super.customMetadata(key, value);
      return this;
    }

    @Override
    public CreateDocumentBatchCommandStep2 customMetadata(
        final Map<String, Object> customMetadata) {
      super.customMetadata(customMetadata);
      return this;
    }

    @Override
    public CreateDocumentBatchCommandStep1 done() {
      return parentStep;
    }
  }
}
