/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_ANONYMOUS_USER;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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

  public T withAuthentication(
      final Function<CamundaAuthentication.Builder, CamundaAuthentication.Builder> fn) {
    return withAuthentication(fn.apply(new CamundaAuthentication.Builder()).build());
  }

  protected <R> CompletableFuture<R> sendBrokerRequest(final BrokerRequest<R> brokerRequest) {
    return sendBrokerRequestWithFullResponse(brokerRequest).thenApply(BrokerResponse::getResponse);
  }

  protected <R> CompletableFuture<BrokerResponse<R>> sendBrokerRequestWithFullResponse(
      final BrokerRequest<R> brokerRequest) {
    final var authorizations =
        Optional.ofNullable(authentication).map(this::toBrokerAuthorization).orElse(null);
    brokerRequest.setAuthorization(authorizations);
    return brokerClient
        .sendRequest(brokerRequest)
        .handleAsync(
            (response, error) -> {
              if (error != null) {
                throw ErrorMapper.mapError(error);
              }
              if (response.isError()) {
                throw ErrorMapper.mapBrokerError(response.getError());
              }
              if (response.isRejection()) {
                throw ErrorMapper.mapBrokerRejection(response.getRejection());
              }
              return response;
            });
  }

  protected DirectBuffer getDocumentOrEmpty(final Map<String, Object> value) {
    return value == null || value.isEmpty()
        ? DocumentValue.EMPTY_DOCUMENT
        : new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value));
  }

  protected Map<String, Object> toBrokerAuthorization(final CamundaAuthentication authentication) {
    final var username = authentication.authenticatedUsername();
    final var clientId = authentication.authenticatedClientId();
    final var claims = authentication.claims();

    final var authorizations = new HashMap<String, Object>();

    authorizations.put(AUTHORIZED_USERNAME, username);
    authorizations.put(AUTHORIZED_CLIENT_ID, clientId);

    if (claims != null) {
      if (claims.containsKey(USER_GROUPS_CLAIMS)) {
        authorizations.put(USER_GROUPS_CLAIMS, claims.get(USER_GROUPS_CLAIMS));
      }

      Optional.ofNullable(claims.get(USER_GROUPS_CLAIMS))
          .ifPresent(v -> authorizations.put(USER_GROUPS_CLAIMS, claims.get(USER_GROUPS_CLAIMS)));
      Optional.ofNullable(claims.get(AUTHORIZED_ANONYMOUS_USER))
          .ifPresent(v -> authorizations.put(AUTHORIZED_ANONYMOUS_USER, v));
    }

    return authorizations;
  }
}
