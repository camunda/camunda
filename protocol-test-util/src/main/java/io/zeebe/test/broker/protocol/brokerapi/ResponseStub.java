/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.broker.protocol.brokerapi;

import java.util.function.Predicate;

public final class ResponseStub<R> {

  protected final Predicate<R> activationFunction;
  protected final MessageBuilder<R> responseWriter;

  public ResponseStub(
      final Predicate<R> activationFunction, final MessageBuilder<R> responseWriter) {
    this.responseWriter = responseWriter;
    this.activationFunction = activationFunction;
  }

  public boolean applies(final R request) {
    return activationFunction.test(request);
  }

  public MessageBuilder<R> getResponseWriter() {
    return responseWriter;
  }

  public boolean shouldRespond() {
    return responseWriter != null;
  }
}
