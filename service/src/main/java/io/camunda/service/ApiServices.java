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
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.gateway.cmd.IllegalTenantRequestException;
import io.camunda.zeebe.gateway.cmd.InvalidTenantRequestException;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.commons.lang3.StringUtils;

public abstract class ApiServices<T extends ApiServices<T>> {
  private static final Pattern TENANT_ID_MASK = Pattern.compile("^[\\w\\.-]{1,31}$");

  protected final BrokerClient brokerClient;
  protected final CamundaSearchClient searchClient;
  protected final Authentication authentication;
  protected final ServiceTransformers transformers;

  protected ApiServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final Authentication authentication) {
    this(brokerClient, searchClient, null, authentication);
  }

  protected ApiServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    this.brokerClient = brokerClient;
    this.searchClient = searchClient;
    this.authentication = authentication;
    this.transformers = Objects.requireNonNullElse(transformers, new ServiceTransformers());
  }

  public abstract T withAuthentication(final Authentication authentication);

  public T withAuthentication(
      final Function<Authentication.Builder, ObjectBuilder<Authentication>> fn) {
    return withAuthentication(fn.apply(new Authentication.Builder()).build());
  }

  protected <R> CompletableFuture<R> sendBrokerRequest(final BrokerRequest<R> brokerRequest) {
    brokerRequest.setAuthorization(authentication.token());
    return brokerClient
        .sendRequest(brokerRequest)
        .handleAsync(
            (response, error) -> {
              if (error != null) {
                throw new CamundaServiceException(error);
              }
              if (response.isError()) {
                throw new CamundaServiceException(response.getError());
              }
              if (response.isRejection()) {
                throw new CamundaServiceException(response.getRejection());
              }
              return response.getResponse();
            });
  }

  protected DirectBuffer getDocumentOrEmpty(final Map<String, Object> value) {
    return value == null || value.isEmpty()
        ? DocumentValue.EMPTY_DOCUMENT
        : new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value));
  }

  protected String ensureTenantIdSet(
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
}
