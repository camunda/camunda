/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public abstract class ApiServices<T extends ApiServices<T>> {

  protected final BrokerClient brokerClient;
  protected final SecurityContextProvider securityContextProvider;
  protected final CamundaAuthentication authentication;

  protected ApiServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final CamundaAuthentication authentication) {
    this.brokerClient = brokerClient;
    this.securityContextProvider = securityContextProvider;
    this.authentication = authentication;
  }

  public abstract T withAuthentication(final CamundaAuthentication authentication);

  protected <R> CompletableFuture<R> sendBrokerRequest(final BrokerRequest<R> brokerRequest) {
    return sendBrokerRequestWithFullResponse(brokerRequest).thenApply(BrokerResponse::getResponse);
  }

  protected <R> CompletableFuture<BrokerResponse<R>> sendBrokerRequestWithFullResponse(
      final BrokerRequest<R> brokerRequest) {
    brokerRequest.setAuthorization(toAuthorization(authentication));
    return brokerClient
        .sendRequest(brokerRequest)
        .handleAsync(
            (response, error) -> {
              if (error != null) {
                throw new CamundaBrokerException(error);
              }
              if (response.isError()) {
                throw new CamundaBrokerException(response.getError());
              }
              if (response.isRejection()) {
                throw new CamundaBrokerException(response.getRejection());
              }
              return response;
            });
  }

  protected DirectBuffer getDocumentOrEmpty(final Map<String, Object> value) {
    return value == null || value.isEmpty()
        ? DocumentValue.EMPTY_DOCUMENT
        : new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value));
  }

  protected Map<String, Object> toAuthorization(final CamundaAuthentication authentication) {
    final var claims = authentication.getClaims();
    final var attributes = new HashMap<String, Object>();

    Optional.ofNullable(authentication.getUsername())
        .ifPresent(u -> attributes.put(Authorization.AUTHORIZED_USERNAME, u));
    Optional.ofNullable(authentication.getClientId())
        .ifPresent(u -> attributes.put(Authorization.AUTHORIZED_CLIENT_ID, u));
    Optional.ofNullable(authentication.getClaims())
        .ifPresent(u -> attributes.put(Authorization.USER_TOKEN_CLAIMS, u));
    Optional.ofNullable(authentication.getGroupIds())
        .ifPresent(u -> attributes.put(Authorization.USER_GROUPS_CLAIMS, u));

    return attributes;
  }
}
