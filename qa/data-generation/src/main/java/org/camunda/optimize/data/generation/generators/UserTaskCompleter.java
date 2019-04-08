/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators;

import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

public class UserTaskCompleter {

  private SimpleEngineClient engineClient;
  private Thread completerThread;

  public UserTaskCompleter(SimpleEngineClient engineClient) {
    this.engineClient = engineClient;
  }

  public void startUserTaskCompletion() {
    completerThread = new Thread(() -> {
      while (!completerThread.isInterrupted()) {
        if (engineClient != null) {
          engineClient.finishAllUserTasks();
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

  public void stopUserTaskCompletion() {
    completerThread.interrupt();
  }


}
