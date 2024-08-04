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
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public abstract class ApiServices<T extends ApiServices<T>> {

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
}
