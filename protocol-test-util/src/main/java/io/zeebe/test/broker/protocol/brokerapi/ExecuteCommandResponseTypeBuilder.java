/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.broker.protocol.brokerapi;

import io.zeebe.test.broker.protocol.MsgPackHelper;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ExecuteCommandResponseTypeBuilder extends ResponseTypeBuilder<ExecuteCommandRequest> {

  protected MsgPackHelper msgPackConverter;

  public ExecuteCommandResponseTypeBuilder(
      Consumer<ResponseStub<ExecuteCommandRequest>> stubConsumer,
      Predicate<ExecuteCommandRequest> activationFunction,
      MsgPackHelper msgPackConverter) {
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
