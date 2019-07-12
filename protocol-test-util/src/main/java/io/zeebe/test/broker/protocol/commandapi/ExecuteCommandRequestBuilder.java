/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.broker.protocol.commandapi;

import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.util.collection.MapBuilder;
import io.zeebe.transport.ClientOutput;
import io.zeebe.util.buffer.BufferWriter;
import java.util.Map;

public class ExecuteCommandRequestBuilder {
  protected ExecuteCommandRequest request;

  public ExecuteCommandRequestBuilder(
      ClientOutput output, int target, MsgPackHelper msgPackHelper) {
    this.request = new ExecuteCommandRequest(output, target, msgPackHelper);
  }

  public ExecuteCommandResponse sendAndAwait() {
    return send().await();
  }

  public ExecuteCommandRequest send() {
    return request.send();
  }

  public ExecuteCommandRequest sendWithoutRetries() {
    return request.send(r -> false);
  }

  public ExecuteCommandRequestBuilder partitionId(int partitionId) {
    request.partitionId(partitionId);
    return this;
  }

  public ExecuteCommandRequestBuilder key(long key) {
    request.key(key);
    return this;
  }

  public ExecuteCommandRequestBuilder type(ValueType valueType, Intent intent) {
    request.valueType(valueType);
    request.intent(intent);
    return this;
  }

  public ExecuteCommandRequestBuilder intent(Intent intent) {
    request.intent(intent);
    return this;
  }

  public ExecuteCommandRequestBuilder command(Map<String, Object> command) {
    request.command(command);
    return this;
  }

  public ExecuteCommandRequestBuilder command(BufferWriter command) {
    request.command(command);
    return this;
  }

  public MapBuilder<ExecuteCommandRequestBuilder> command() {
    final MapBuilder<ExecuteCommandRequestBuilder> mapBuilder =
        new MapBuilder<>(this, this::command);
    return mapBuilder;
  }
}
