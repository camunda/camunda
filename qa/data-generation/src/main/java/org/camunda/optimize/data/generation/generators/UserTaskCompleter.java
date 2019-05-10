/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators;

import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.concurrent.CountDownLatch;

public class UserTaskCompleter {

  private SimpleEngineClient engineClient;
  private CountDownLatch finished = new CountDownLatch(1);
  public UserTaskCompleter(SimpleEngineClient engineClient) {
    this.engineClient = engineClient;
  }

  public void startUserTaskCompletion() {
    Thread completerThread = new Thread(() -> {
      boolean allUserTasksCompleted = false;
      while (!allUserTasksCompleted) {
        allUserTasksCompleted = engineClient.finishAllUserTasks();
      }
      finished.countDown();
    });
    completerThread.start();
  }

  public void awaitUserTaskCompletion() {
    try {
      finished.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }


}
