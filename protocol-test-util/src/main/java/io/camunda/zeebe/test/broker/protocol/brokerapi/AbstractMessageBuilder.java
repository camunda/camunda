/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol.brokerapi;

public abstract class AbstractMessageBuilder<T> implements MessageBuilder<T> {

  protected Runnable beforeResponse;

  @Override
  public void beforeResponse() {
    if (beforeResponse != null) {
      beforeResponse.run();
    }
  }

  public void beforeResponse(final Runnable beforeResponse) {
    this.beforeResponse = beforeResponse;
  }
}
