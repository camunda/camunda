/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.data.generation.generators;

import io.camunda.optimize.test.util.client.SimpleEngineClient;
import lombok.Getter;

public class MessageEventCorrelater {

  private final SimpleEngineClient engineClient;

  @Getter
  private final String[] messagesToCorrelate;

  public MessageEventCorrelater(final SimpleEngineClient engineClient,
      final String[] messagesToCorrelate) {
    this.engineClient = engineClient;
    this.messagesToCorrelate = messagesToCorrelate;
  }

  public void correlateMessages() {
    for (final String messageName : messagesToCorrelate) {
      engineClient.correlateMessage(messageName);
    }
  }
}
