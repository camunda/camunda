/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol.commandapi;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.test.broker.protocol.MsgPackHelper;
import io.camunda.zeebe.test.util.collection.MapBuilder;
import io.camunda.zeebe.transport.ClientTransport;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Map;

public final class ExecuteCommandRequestBuilder {
  protected final ExecuteCommandRequest request;

  public ExecuteCommandRequestBuilder(
      final ClientTransport output, final String targetAddress, final MsgPackHelper msgPackHelper) {
    request = new ExecuteCommandRequest(output, targetAddress, msgPackHelper);
  }

  public ExecuteCommandResponse sendAndAwait() {
    return send().await();
  }

  public ExecuteCommandRequest send() {
    return request.send();
  }

  public ExecuteCommandRequestBuilder partitionId(final int partitionId) {
    request.partitionId(partitionId);
    return this;
  }

  public ExecuteCommandRequestBuilder key(final long key) {
    request.key(key);
    return this;
  }

  public ExecuteCommandRequestBuilder type(final ValueType valueType, final Intent intent) {
    request.valueType(valueType);
    request.intent(intent);
    return this;
  }

  public ExecuteCommandRequestBuilder intent(final Intent intent) {
    request.intent(intent);
    return this;
  }

  public ExecuteCommandRequestBuilder command(final Map<String, Object> command) {
    request.command(command);
    return this;
  }

  public ExecuteCommandRequestBuilder command(final BufferWriter command) {
    request.command(command);
    return this;
  }

  public MapBuilder<ExecuteCommandRequestBuilder> command() {
    final MapBuilder<ExecuteCommandRequestBuilder> mapBuilder =
        new MapBuilder<>(this, this::command);
    return mapBuilder;
  }
}
