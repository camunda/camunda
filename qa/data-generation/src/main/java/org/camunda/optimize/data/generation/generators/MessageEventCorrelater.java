/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.generators;

import lombok.Getter;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

public class MessageEventCorrelater {

  private SimpleEngineClient engineClient;

  @Getter
  private String[] messagesToCorrelate;

  public MessageEventCorrelater(SimpleEngineClient engineClient, String[] messagesToCorrelate) {
    this.engineClient = engineClient;
    this.messagesToCorrelate = messagesToCorrelate;
  }

  public void correlateMessages() {
    for (String messageName : messagesToCorrelate) {
      engineClient.correlateMessage(messageName);
    }
  }

}
