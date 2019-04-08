/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators;

import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

public class MessageEventCorrelater {

  private SimpleEngineClient engineClient;
  private Thread completerThread;
  private String[] messagesToCorrelate;

  public MessageEventCorrelater(SimpleEngineClient engineClient, String[] messagesToCorrelate) {
    this.engineClient = engineClient;
    this.messagesToCorrelate = messagesToCorrelate;
  }

  public void startCorrelatingMessages() {
    if (messagesToCorrelate.length > 0) {
      completerThread = new Thread(() -> {
        while (!completerThread.isInterrupted()) {
          if (engineClient != null) {
            correlateMessages();
          }
          try {
            Thread.sleep(2000L);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      });
      completerThread.start();
    }
  }

  public void correlateMessages() {
    for (String messageName : messagesToCorrelate) {
      engineClient.correlateMessage(messageName);
    }
  }

  public void stopCorrelatingMessages() {
    if (messagesToCorrelate.length > 0) {
      completerThread.interrupt();
    }
  }


}
