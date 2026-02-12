/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public abstract class ApiServices<T extends ApiServices<T>> {

  protected final BrokerClient brokerClient;
  protected final SecurityContextProvider securityContextProvider;
  protected final CamundaAuthentication authentication;
  protected final ApiServicesExecutorProvider executorProvider;
  protected final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;
  private final ExecutorService executor;

  protected ApiServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    this.brokerClient = brokerClient;
    this.securityContextProvider = securityContextProvider;
    this.authentication = authentication;
    this.executorProvider = executorProvider;
    executor = executorProvider.getExecutor();
    this.brokerRequestAuthorizationConverter = brokerRequestAuthorizationConverter;
  }

  public abstract T withAuthentication(final CamundaAuthentication authentication);

  public T withAuthentication(
      final Function<CamundaAuthentication.Builder, CamundaAuthentication.Builder> fn) {
    return withAuthentication(fn.apply(new CamundaAuthentication.Builder()).build());
  }

  protected <R> CompletableFuture<R> sendBrokerRequest(final BrokerRequest<R> brokerRequest) {
    return sendBrokerRequestWithFullResponse(brokerRequest)
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }

  protected <R> CompletableFuture<R> sendBrokerRequest(
      final BrokerRequest<R> brokerRequest, final Duration requestTimeout) {
    return sendBrokerRequestWithFullResponse(brokerRequest, requestTimeout)
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }

  protected <R> CompletableFuture<BrokerResponse<R>> sendBrokerRequestWithFullResponse(
      final BrokerRequest<R> brokerRequest) {
    final var brokerRequestAuthorization =
        brokerRequestAuthorizationConverter.convert(authentication);
    brokerRequest.setAuthorization(brokerRequestAuthorization);
    return brokerClient.sendRequest(brokerRequest).handleAsync(handleBrokerResponse(), executor);
  }

  protected <R> CompletableFuture<BrokerResponse<R>> sendBrokerRequestWithFullResponse(
      final BrokerRequest<R> brokerRequest, final Duration requestTimeout) {
    final var brokerRequestAuthorization =
        brokerRequestAuthorizationConverter.convert(authentication);
    brokerRequest.setAuthorization(brokerRequestAuthorization);
    return brokerClient
        .sendRequest(brokerRequest, requestTimeout)
        .handleAsync(handleBrokerResponse(), executor);
  }

  private <R>
      java.util.function.BiFunction<BrokerResponse<R>, Throwable, BrokerResponse<R>>
          handleBrokerResponse() {
    return (response, error) -> {
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
    };
  }

  protected DirectBuffer getDocumentOrEmpty(final Map<String, Object> value) {
    return value == null || value.isEmpty()
        ? DocumentValue.EMPTY_DOCUMENT
        : new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value));
  }
}
