/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.security.auth.Authentication;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public abstract class ApiServices<T extends ApiServices<T>> {

  protected final BrokerClient brokerClient;
  protected final Authentication authentication;

  protected ApiServices(final BrokerClient brokerClient, final Authentication authentication) {
    this.brokerClient = brokerClient;
    this.authentication = authentication;
  }

  public abstract T withAuthentication(final Authentication authentication);

  public T withAuthentication(
      final Function<Authentication.Builder, ObjectBuilder<Authentication>> fn) {
    return withAuthentication(fn.apply(new Authentication.Builder()).build());
  }

  protected <R> CompletableFuture<R> sendBrokerRequest(final BrokerRequest<R> brokerRequest) {
    return sendBrokerRequestWithFullResponse(brokerRequest).thenApply(BrokerResponse::getResponse);
  }

  protected <R> CompletableFuture<BrokerResponse<R>> sendBrokerRequestWithFullResponse(
      final BrokerRequest<R> brokerRequest) {
    brokerRequest.setAuthorization(authentication.token());
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
}
