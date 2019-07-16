/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.broker.protocol.brokerapi;

import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class ResponseTypeBuilder<R> {

  protected final Consumer<ResponseStub<R>> stubConsumer;
  protected final Predicate<R> activationFunction;

  public ResponseTypeBuilder(
      Consumer<ResponseStub<R>> stubConsumer, Predicate<R> activationFunction) {
    this.activationFunction = activationFunction;
    this.stubConsumer = stubConsumer;
  }

  protected void respondWith(MessageBuilder<R> responseBuilder) {
    final ResponseStub<R> responseStub = new ResponseStub<>(activationFunction, responseBuilder);
    stubConsumer.accept(responseStub);
  }

  public void doNotRespond() {
    respondWith(null);
  }
}
