/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;
import org.camunda.optimize.data.generation.generators.client.dto.TaskDto;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class UserTaskCompleter {

  private static final int TASKS_TO_FETCH = 1000;
  private SimpleEngineClient engineClient;
  private CountDownLatch finished = new CountDownLatch(1);
  private Set<String> tasksToNotComplete = new HashSet<>();

  public UserTaskCompleter(SimpleEngineClient engineClient) {
    this.engineClient = engineClient;
  }

  public void startUserTaskCompletion() {
    Thread completerThread = new Thread(() -> {
      boolean allUserTasksCompleted = false;
      while (!allUserTasksCompleted) {
        List<TaskDto> tasks = engineClient.getAllTasks(TASKS_TO_FETCH + tasksToNotComplete.size());

        allUserTasksCompleted = allTasksCompleted(tasks);

        if (tasks.size() != 0) {
          log.info(engineClient.getAllTasksCount() - tasksToNotComplete.size() + " user tasks left to complete");
        }

        for (TaskDto task : tasks) {
          claimAndCompleteUserTask(task);
        }
      }
      finished.countDown();
    });
    completerThread.start();
  }

  private void claimAndCompleteUserTask(TaskDto task) {
    Random random = new Random();
    try {
      engineClient.addOrRemoveIdentityLinks(task);
      engineClient.claimTask(task);

      if (random.nextDouble() > 0.95) {
        engineClient.unclaimTask(task);
      } else if (!isTaskToComplete(task.getId())) {
        if (random.nextDouble() < 0.97) {
          engineClient.completeUserTask(task);
        } else {
          setDoNotCompleteFlag(task);
        }
      }
    } catch (Exception e) {
      log.error("Could not claim user task!", e);
    }
  }

  private Boolean isTaskToComplete(String taskId) {
    return tasksToNotComplete.contains(taskId);
  }


  private void setDoNotCompleteFlag(TaskDto task) throws IOException {
    tasksToNotComplete.add(task.getId());
  }

  private boolean allTasksCompleted(List<TaskDto> tasks) {
    return tasks.size() != 0 && tasks.size() == tasksToNotComplete.size() &&
      tasks.stream().allMatch(t -> tasksToNotComplete.contains(t.getId()));
  }

  public void awaitUserTaskCompletion() {
    try {
      finished.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
