/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.broker.protocol.brokerapi;

import io.zeebe.test.broker.protocol.MsgPackHelper;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ExecuteCommandResponseTypeBuilder
    extends ResponseTypeBuilder<ExecuteCommandRequest> {

  protected final MsgPackHelper msgPackConverter;

  public ExecuteCommandResponseTypeBuilder(
      final Consumer<ResponseStub<ExecuteCommandRequest>> stubConsumer,
      final Predicate<ExecuteCommandRequest> activationFunction,
      final MsgPackHelper msgPackConverter) {
    super(stubConsumer, activationFunction);
    this.msgPackConverter = msgPackConverter;
  }

  public ExecuteCommandResponseBuilder respondWith() {
    return new ExecuteCommandResponseBuilder(this::respondWith, msgPackConverter);
  }

  public ErrorResponseBuilder<ExecuteCommandRequest> respondWithError() {
    return new ErrorResponseBuilder<>(this::respondWith, msgPackConverter);
  }
}
