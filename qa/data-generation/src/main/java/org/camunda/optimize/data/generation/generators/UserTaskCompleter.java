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
