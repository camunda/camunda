/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.broker.protocol.brokerapi;

import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import java.util.function.Consumer;

public class ErrorResponseBuilder<R> {
  protected final Consumer<MessageBuilder<R>> registrationFunction;
  protected final ErrorResponseWriter<R> commandResponseWriter;

  public ErrorResponseBuilder(
      Consumer<MessageBuilder<R>> registrationFunction, MsgPackHelper msgPackConverter) {
    this.registrationFunction = registrationFunction;
    this.commandResponseWriter = new ErrorResponseWriter<>(msgPackConverter);
  }

  public ErrorResponseBuilder<R> errorCode(ErrorCode errorCode) {
    this.commandResponseWriter.setErrorCode(errorCode);
    return this;
  }

  public ErrorResponseBuilder<R> errorData(String errorData) {
    this.commandResponseWriter.setErrorData(errorData);
    return this;
  }

  public void register() {
    registrationFunction.accept(commandResponseWriter);
  }

  /**
   * Blocks before responding; continues sending the response only when {@link
   * ResponseController#unblockNextResponse()} is called.
   */
  public ResponseController registerControlled() {
    final ResponseController controller = new ResponseController();
    commandResponseWriter.beforeResponse(controller::waitForNextJoin);
    register();
    return controller;
  }
}
