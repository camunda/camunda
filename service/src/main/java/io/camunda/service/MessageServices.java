/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCorrelateMessageRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.camunda.zeebe.gateway.validation.VariableNameLengthValidator;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.ChannelType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

public final class MessageServices extends PhysicalTenantScopedApiServices<MessageServices> {
  private final int maxVariableNameLength;

  public MessageServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    this(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter,
        VariableNameLengthValidator.DEFAULT_MAX_NAME_FIELD_LENGTH);
  }

  public MessageServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final int maxVariableNameLength) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.maxVariableNameLength = maxVariableNameLength;
  }

  public CompletableFuture<MessageCorrelationRecord> correlateMessage(
      final CorrelateMessageRequest correlationRequest,
      final CamundaAuthentication authentication) {
    return correlateMessage(correlationRequest, authentication, null, null);
  }

  public CompletableFuture<MessageCorrelationRecord> correlateMessage(
      final CorrelateMessageRequest correlationRequest,
      final CamundaAuthentication authentication,
      final @Nullable ChannelType channelType,
      final @Nullable String toolName) {
    final var brokerRequest =
        new BrokerCorrelateMessageRequest(
                correlationRequest.name, correlationRequest.correlationKey, maxVariableNameLength)
            .setVariables(getDocumentOrEmpty(correlationRequest.variables))
            .setTenantId(correlationRequest.tenantId)
            .setBusinessId(correlationRequest.businessId);
    if (channelType != null) {
      brokerRequest.setChannelType(channelType);
    }
    if (toolName != null) {
      brokerRequest.setToolName(toolName);
    }
    return sendBrokerRequest(brokerRequest, authentication);
  }

  public CompletableFuture<BrokerResponse<MessageRecord>> publishMessage(
      final PublicationMessageRequest request, final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerPublishMessageRequest(request.name, request.correlationKey)
            .setTimeToLive(request.timeToLive)
            .setMessageId(request.messageId)
            .setVariables(getDocumentOrEmpty(request.variables))
            .setTenantId(request.tenantId)
            .setBusinessId(request.businessId);
    return sendBrokerRequestWithFullResponse(brokerRequest, authentication);
  }

  public record CorrelateMessageRequest(
      String name,
      String correlationKey,
      Map<String, Object> variables,
      String tenantId,
      @Nullable String businessId) {
    public CorrelateMessageRequest(
        final String name,
        final String correlationKey,
        final Map<String, Object> variables,
        final String tenantId) {
      this(name, correlationKey, variables, tenantId, null);
    }
  }

  public record PublicationMessageRequest(
      String name,
      String correlationKey,
      Long timeToLive,
      String messageId,
      Map<String, Object> variables,
      String tenantId,
      @Nullable String businessId) {}
}
