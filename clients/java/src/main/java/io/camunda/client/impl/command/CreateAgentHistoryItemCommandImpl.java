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
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryContent;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryContent.DocumentContent;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryContent.ObjectContent;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryContent.TextContent;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryMetrics;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryRole;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryToolCall;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.CreateAgentHistoryItemCommandStep2;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.CreateAgentHistoryItemCommandStep3;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.CreateAgentHistoryItemCommandStep4;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.CreateAgentHistoryItemCommandStep5;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.CreateAgentHistoryItemFinalCommandStep;
import io.camunda.client.api.response.CreateAgentHistoryItemResponse;
import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateAgentHistoryItemResponseImpl;
import io.camunda.client.protocol.rest.AgentInstanceDocumentContent;
import io.camunda.client.protocol.rest.AgentInstanceHistoryItemCreationResult;
import io.camunda.client.protocol.rest.AgentInstanceHistoryItemMetrics;
import io.camunda.client.protocol.rest.AgentInstanceHistoryItemRequest;
import io.camunda.client.protocol.rest.AgentInstanceHistoryRoleEnum;
import io.camunda.client.protocol.rest.AgentInstanceMessageContent;
import io.camunda.client.protocol.rest.AgentInstanceObjectContent;
import io.camunda.client.protocol.rest.AgentInstanceTextContent;
import io.camunda.client.protocol.rest.AgentInstanceToolCall;
import io.camunda.client.protocol.rest.DocumentMetadataResponse;
import io.camunda.client.protocol.rest.DocumentReference;
import io.camunda.client.protocol.rest.DocumentReference.CamundaDocumentTypeEnum;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateAgentHistoryItemCommandImpl
    implements CreateAgentHistoryItemCommandStep1,
        CreateAgentHistoryItemCommandStep2,
        CreateAgentHistoryItemCommandStep3,
        CreateAgentHistoryItemCommandStep4,
        CreateAgentHistoryItemCommandStep5,
        CreateAgentHistoryItemFinalCommandStep {

  private final AgentInstanceHistoryItemRequest request;
  private final long agentInstanceKey;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateAgentHistoryItemCommandImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long agentInstanceKey) {
    ArgumentUtil.ensureGreaterThan("agentInstanceKey", agentInstanceKey, 0);
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    this.agentInstanceKey = agentInstanceKey;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new AgentInstanceHistoryItemRequest();
  }

  @Override
  public CreateAgentHistoryItemCommandStep2 elementInstanceKey(final long elementInstanceKey) {
    ArgumentUtil.ensureGreaterThan("elementInstanceKey", elementInstanceKey, 0);
    request.elementInstanceKey(String.valueOf(elementInstanceKey));
    return this;
  }

  @Override
  public CreateAgentHistoryItemCommandStep3 jobKey(final long jobKey) {
    ArgumentUtil.ensureGreaterThan("jobKey", jobKey, 0);
    request.jobKey(String.valueOf(jobKey));
    return this;
  }

  @Override
  public CreateAgentHistoryItemCommandStep4 role(final AgentHistoryRole role) {
    ArgumentUtil.ensureNotNull("role", role);
    request.role(AgentInstanceHistoryRoleEnum.fromValue(role.name()));
    return this;
  }

  @Override
  public CreateAgentHistoryItemCommandStep5 content(final List<AgentHistoryContent> content) {
    ArgumentUtil.ensureNotNull("content", content);
    if (content.isEmpty()) {
      throw new IllegalArgumentException("content must not be empty");
    }
    final List<AgentInstanceMessageContent> protocolContent = new ArrayList<>(content.size());
    for (final AgentHistoryContent item : content) {
      if (item == null) {
        throw new IllegalArgumentException("content must not contain null elements");
      }
      if (item instanceof TextContent) {
        final String text = ((TextContent) item).getText();
        if (text == null) {
          throw new IllegalArgumentException("text content value must not be null");
        }
        protocolContent.add(new AgentInstanceTextContent().text(text));
      } else if (item instanceof ObjectContent) {
        final Object obj = ((ObjectContent) item).getObject();
        if (obj == null) {
          throw new IllegalArgumentException("object content value must not be null");
        }
        protocolContent.add(
            new AgentInstanceObjectContent()._object(((ObjectContent) item).getObject()));
      } else if (item instanceof DocumentContent) {
        protocolContent.add(toProtocolDocumentContent((DocumentContent) item));
      } else {
        throw new IllegalArgumentException("Unsupported AgentHistoryContent type: " + item);
      }
    }
    request.content(protocolContent);
    return this;
  }

  private AgentInstanceDocumentContent toProtocolDocumentContent(final DocumentContent item) {
    final DocumentReferenceResponse ref = item.getDocumentReference();
    if (ref.getDocumentId() == null || ref.getDocumentId().trim().isEmpty()) {
      throw new IllegalArgumentException("documentReference.documentId must not be null or blank");
    }
    final DocumentReference protocolRef =
        new DocumentReference()
            .camundaDocumentType(CamundaDocumentTypeEnum.CAMUNDA)
            .documentId(ref.getDocumentId())
            .storeId(ref.getStoreId());
    if (ref.getContentHash() != null) {
      protocolRef.contentHash(ref.getContentHash());
    }
    final DocumentMetadata metadata = ref.getMetadata();
    if (metadata != null) {
      protocolRef.metadata(toProtocolDocumentMetadata(metadata));
    }
    return new AgentInstanceDocumentContent().documentReference(protocolRef);
  }

  private DocumentMetadataResponse toProtocolDocumentMetadata(final DocumentMetadata metadata) {
    final DocumentMetadataResponse protocolMeta = new DocumentMetadataResponse();
    if (metadata.getFileName() != null) {
      protocolMeta.fileName(metadata.getFileName());
    }
    if (metadata.getContentType() != null) {
      protocolMeta.contentType(metadata.getContentType());
    }
    if (metadata.getSize() != null) {
      protocolMeta.size(metadata.getSize());
    }
    if (metadata.getExpiresAt() != null) {
      protocolMeta.expiresAt(metadata.getExpiresAt().toString());
    }
    if (metadata.getProcessDefinitionId() != null) {
      protocolMeta.processDefinitionId(metadata.getProcessDefinitionId());
    }
    if (metadata.getProcessInstanceKey() != null) {
      protocolMeta.processInstanceKey(String.valueOf(metadata.getProcessInstanceKey()));
    }
    if (metadata.getCustomProperties() != null && !metadata.getCustomProperties().isEmpty()) {
      protocolMeta.customProperties(metadata.getCustomProperties());
    }
    return protocolMeta;
  }

  @Override
  public CreateAgentHistoryItemFinalCommandStep producedAt(final OffsetDateTime producedAt) {
    ArgumentUtil.ensureNotNull("producedAt", producedAt);
    request.producedAt(producedAt.toString());
    return this;
  }

  @Override
  public CreateAgentHistoryItemFinalCommandStep jobLease(final String jobLease) {
    ArgumentUtil.ensureNotNull("jobLease", jobLease);
    if (jobLease.trim().isEmpty()) {
      throw new IllegalArgumentException("jobLease must not be blank");
    }
    request.jobLease(jobLease);
    return this;
  }

  @Override
  public CreateAgentHistoryItemFinalCommandStep iteration(final int iteration) {
    ArgumentUtil.ensureGreaterThan("iteration", iteration, 0);
    request.iteration(iteration);
    return this;
  }

  @Override
  public CreateAgentHistoryItemFinalCommandStep toolCalls(
      final List<AgentHistoryToolCall> toolCalls) {
    if (toolCalls == null) {
      return this;
    }
    final List<AgentInstanceToolCall> protocolToolCalls = new ArrayList<>(toolCalls.size());
    for (final AgentHistoryToolCall tc : toolCalls) {
      if (tc == null) {
        throw new IllegalArgumentException("toolCalls must not contain null elements");
      }
      if (tc.getToolCallId() == null || tc.getToolCallId().trim().isEmpty()) {
        throw new IllegalArgumentException("toolCallId must not be null or blank");
      }
      if (tc.getToolName() == null || tc.getToolName().trim().isEmpty()) {
        throw new IllegalArgumentException("toolName must not be null or blank");
      }
      protocolToolCalls.add(
          new AgentInstanceToolCall()
              .toolCallId(tc.getToolCallId())
              .toolName(tc.getToolName())
              .elementId(tc.getElementId())
              .arguments(tc.getArguments()));
    }
    request.toolCalls(protocolToolCalls);
    return this;
  }

  @Override
  public CreateAgentHistoryItemFinalCommandStep metrics(final AgentHistoryMetrics metrics) {
    if (metrics == null) {
      return this;
    }
    if (metrics.getInputTokens() == null
        || metrics.getOutputTokens() == null
        || metrics.getDurationMs() == null) {
      throw new IllegalArgumentException(
          "metrics requires all fields: inputTokens, outputTokens, durationMs");
    }
    request.metrics(
        new AgentInstanceHistoryItemMetrics()
            .inputTokens(metrics.getInputTokens())
            .outputTokens(metrics.getOutputTokens())
            .durationMs(metrics.getDurationMs()));
    return this;
  }

  @Override
  public CreateAgentHistoryItemFinalCommandStep requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateAgentHistoryItemResponse> send() {
    final HttpCamundaFuture<CreateAgentHistoryItemResponse> result = new HttpCamundaFuture<>();
    final CreateAgentHistoryItemResponseImpl response = new CreateAgentHistoryItemResponseImpl();
    httpClient.post(
        "/agent-instances/" + agentInstanceKey + "/history",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        AgentInstanceHistoryItemCreationResult.class,
        response::setResponse,
        result);
    return result;
  }
}
