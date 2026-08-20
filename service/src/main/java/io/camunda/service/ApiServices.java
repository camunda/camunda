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
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public abstract class ApiServices<T extends ApiServices<T>> {

  protected final BrokerClient brokerClient;
  protected final SecurityContextProvider securityContextProvider;
  protected final ApiServicesExecutorProvider executorProvider;
  protected final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;
  private final ExecutorService executor;

  protected ApiServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    this.brokerClient = brokerClient;
    this.securityContextProvider = securityContextProvider;
    this.executorProvider = executorProvider;
    executor = executorProvider.getExecutor();
    this.brokerRequestAuthorizationConverter = brokerRequestAuthorizationConverter;
  }

  protected final <R> CompletableFuture<R> sendBrokerRequest(
      final BrokerRequest<R> brokerRequest, final CamundaAuthentication authentication) {
    return sendBrokerRequestWithFullResponse(brokerRequest, authentication)
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }

  protected final <R> CompletableFuture<BrokerResponse<R>> sendBrokerRequestWithFullResponse(
      final BrokerRequest<R> brokerRequest, final CamundaAuthentication authentication) {
    applyBrokerRequestMutators(brokerRequest, authentication);
    return brokerClient.sendRequest(brokerRequest).handleAsync(handleBrokerResponse(), executor);
  }

  /**
   * Applies every {@link #brokerRequestMutators() mutator} to the given request. Send paths that
   * cannot use {@link #sendBrokerRequest} / {@link #sendBrokerRequestWithFullResponse} (e.g.
   * because they dispatch via a partition-retry or job-activation handler and therefore bypass
   * these helpers) must call this so they get the full mutator set (authorization <em>and</em>, for
   * physical-tenant-scoped services, the partition group). Hand-applying a subset (e.g. only
   * authorization) silently drops newer mutators and is the bug class this method exists to
   * prevent.
   */
  protected final void applyBrokerRequestMutators(
      final BrokerRequest<?> brokerRequest, final CamundaAuthentication authentication) {
    brokerRequestMutators().forEach(mutator -> mutator.accept(brokerRequest, authentication));
  }

  protected List<BiConsumer<BrokerRequest, CamundaAuthentication>> brokerRequestMutators() {
    return List.of(
        (brokerRequest, authentication) -> {
          final var brokerRequestAuthorization =
              brokerRequestAuthorizationConverter.convert(authentication);
          brokerRequest.setAuthorization(brokerRequestAuthorization);
        });
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
