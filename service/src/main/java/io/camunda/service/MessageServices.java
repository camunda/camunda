/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.PROCESS_INSTANCE_READ_AUTHORIZATION;

import io.camunda.search.clients.CorrelatedMessageSearchClient;
import io.camunda.search.entities.CorrelatedMessageEntity;
import io.camunda.search.query.CorrelatedMessageQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCorrelateMessageRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class MessageServices
    extends SearchQueryService<MessageServices, CorrelatedMessageQuery, CorrelatedMessageEntity> {

  private final CorrelatedMessageSearchClient searchClient;

  public MessageServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final CorrelatedMessageSearchClient searchClient,
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.searchClient = searchClient;
  }

  @Override
  public MessageServices withAuthentication(final CamundaAuthentication authentication) {
    return new MessageServices(brokerClient, securityContextProvider, searchClient, authentication);
  }

  public CompletableFuture<MessageCorrelationRecord> correlateMessage(
      final CorrelateMessageRequest correlationRequest) {
    final var brokerRequest =
        new BrokerCorrelateMessageRequest(
                correlationRequest.name, correlationRequest.correlationKey)
            .setVariables(getDocumentOrEmpty(correlationRequest.variables))
            .setTenantId(correlationRequest.tenantId);
    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<BrokerResponse<MessageRecord>> publishMessage(
      final PublicationMessageRequest request) {
    final var brokerRequest =
        new BrokerPublishMessageRequest(request.name, request.correlationKey)
            .setTimeToLive(request.timeToLive)
            .setMessageId(request.messageId)
            .setVariables(getDocumentOrEmpty(request.variables))
            .setTenantId(request.tenantId);
    return sendBrokerRequestWithFullResponse(brokerRequest);
  }

  @Override
  public SearchQueryResult<CorrelatedMessageEntity> search(final CorrelatedMessageQuery query) {
    return executeSearchRequest(
        () ->
            searchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, PROCESS_INSTANCE_READ_AUTHORIZATION))
                .searchCorrelatedMessages(query));
  }

  public record CorrelateMessageRequest(
      String name, String correlationKey, Map<String, Object> variables, String tenantId) {}

  public record PublicationMessageRequest(
      String name,
      String correlationKey,
      Long timeToLive,
      String messageId,
      Map<String, Object> variables,
      String tenantId) {}
}
