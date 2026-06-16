/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.query.impl;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.IllegalBrokerResponseException;
import io.camunda.zeebe.gateway.query.QueryApi;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class QueryApiImpl implements QueryApi {
  private final BrokerClient client;

  public QueryApiImpl(final BrokerClient client) {
    this.client = client;
  }

  @Override
  public CompletionStage<String> getBpmnProcessIdFromProcess(
      final long key, final Duration timeout) {
    return queryPartition(key, ValueType.PROCESS, timeout);
  }

  @Override
  public CompletionStage<String> getBpmnProcessIdFromProcessInstance(
      final long key, final Duration timeout) {
    return queryPartition(key, ValueType.PROCESS_INSTANCE, timeout);
  }

  @Override
  public CompletionStage<String> getBpmnProcessIdFromJob(final long key, final Duration timeout) {
    return queryPartition(key, ValueType.JOB, timeout);
  }

  private CompletionStage<String> queryPartition(
      final long key, final ValueType valueType, final Duration timeout) {
    final CompletableFuture<String> result = new CompletableFuture<>();

    try {
      sendRequest(key, valueType, timeout, result);
    } catch (final Exception e) {
      result.completeExceptionally(e);
    }

    return result;
  }

  private void sendRequest(
      final long key,
      final ValueType valueType,
      final Duration timeout,
      final CompletableFuture<String> result) {
    final var request = new BrokerExecuteQuery();
    final var partitionId = Protocol.decodePartitionId(key);

    request.setKey(key);
    request.setPartitionId(partitionId);
    request.setValueType(valueType);

    client
        .sendRequestWithRetry(request, timeout)
        .whenComplete(
            (response, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else if (response.isResponse()) {
                result.complete(response.getResponse());
              } else if (response.isError()) {
                result.completeExceptionally(new BrokerErrorException(response.getError()));
              } else if (response.isRejection()) {
                result.completeExceptionally(new BrokerRejectionException(response.getRejection()));
              } else {
                result.completeExceptionally(
                    new IllegalBrokerResponseException(
                        "Expected broker response to be either response, rejection, or error, but is neither of them"));
              }
            });
  }
}
