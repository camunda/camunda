/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.query.impl;

import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
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
              } else {
                result.complete(response.getResponse());
              }
            });
  }
}
