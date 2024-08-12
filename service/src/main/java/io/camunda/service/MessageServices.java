/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.cmd.IllegalTenantRequestException;
import io.camunda.zeebe.gateway.cmd.InvalidTenantRequestException;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCorrelateMessageRequest;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public final class MessageServices<T> extends ApiServices<MessageServices<T>> {

  private static final Pattern TENANT_ID_MASK = Pattern.compile("^[\\w\\.-]{1,31}$");

  public MessageServices(final BrokerClient brokerClient, final CamundaSearchClient searchClient) {
    this(brokerClient, searchClient, null, null);
  }

  public MessageServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public MessageServices<T> withAuthentication(final Authentication authentication) {
    return new MessageServices<>(brokerClient, searchClient, transformers, authentication);
  }

  public CompletableFuture<MessageCorrelationRecord> correlateMessage(
      final CorrelateMessageRequest correlationRequest, final boolean multiTenancyEnabled) {
    final var brokerRequest =
        new BrokerCorrelateMessageRequest(
                correlationRequest.name, correlationRequest.correlationKey)
            .setVariables(getDocumentOrEmpty(correlationRequest.variables))
            .setTenantId(
                ensureTenantIdSet(
                    "CorrelateMessage", correlationRequest.tenantId, multiTenancyEnabled));
    return sendBrokerRequest(brokerRequest);
  }

  private String ensureTenantIdSet(
      final String commandName, final String tenantId, final boolean multiTenancyEnabled) {
    final var hasTenantId = !StringUtils.isBlank(tenantId);
    if (!multiTenancyEnabled) {
      if (hasTenantId && !TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId)) {
        throw new InvalidTenantRequestException(commandName, tenantId, "multi-tenancy is disabled");
      }

      return TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    }

    if (!hasTenantId) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "no tenant identifier was provided.");
    }

    if (tenantId.length() > 31) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "tenant identifier is longer than 31 characters");
    }

    if (!TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId)
        && !TENANT_ID_MASK.matcher(tenantId).matches()) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "tenant identifier contains illegal characters");
    }

    final List<String> authorizedTenants;
    try {
      authorizedTenants = authentication.authenticatedTenantIds();
    } catch (final Exception e) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "tenant could not be retrieved from the request context", e);
    }
    if (authorizedTenants == null) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "tenant could not be retrieved from the request context");
    }
    if (!authorizedTenants.contains(tenantId)) {
      throw new IllegalTenantRequestException(
          commandName, tenantId, "tenant is not authorized to perform this request");
    }

    return tenantId;
  }

  public record CorrelateMessageRequest(
      String name, String correlationKey, Map<String, Object> variables, String tenantId) {}
}
