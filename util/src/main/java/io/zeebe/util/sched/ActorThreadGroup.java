/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.ActorScheduler.ActorSchedulerBuilder;
import io.zeebe.util.sched.metrics.ActorThreadMetrics;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A thread group is a group of threads which process the same kind of tasks (ie. blocking I/O vs.
 * CPU bound).
 */
public abstract class ActorThreadGroup {
  protected final String groupName;

  protected int numOfThreads;

  protected final ActorThread[] threads;
  protected final MultiLevelWorkstealingGroup tasks;

  public ActorThreadGroup(
      String groupName, int numOfThreads, int numOfQueuesPerThread, ActorSchedulerBuilder builder) {
    this.groupName = groupName;
    this.numOfThreads = numOfThreads;

    this.tasks = new MultiLevelWorkstealingGroup(numOfThreads, numOfQueuesPerThread);

    threads = new ActorThread[numOfThreads];

    for (int t = 0; t < numOfThreads; t++) {
      final String threadName = String.format("%s-%d", groupName, t);
      final ActorThreadMetrics metrics =
          new ActorThreadMetrics(threadName, builder.getMetricsManager());
      final TaskScheduler taskScheduler = createTaskScheduler(tasks, builder);

      final ActorThread thread =
          builder
              .getActorThreadFactory()
              .newThread(
                  threadName,
                  t,
                  this,
                  taskScheduler,
                  builder.getActorClock(),
                  metrics,
                  builder.getActorTimerQueue());

      threads[t] = thread;
    }
  }

  protected abstract TaskScheduler createTaskScheduler(
      MultiLevelWorkstealingGroup tasks, ActorSchedulerBuilder builder);

  public void submit(ActorTask actorTask) {
    final int level = getLevel(actorTask);

    final ActorThread current = ActorThread.current();
    if (current != null && current.getActorThreadGroup() == this) {
      tasks.submit(actorTask, level, current.getRunnerId());
    } else {
      final int threadId = ThreadLocalRandom.current().nextInt(numOfThreads);
      tasks.submit(actorTask, level, threadId);
      threads[threadId].hintWorkAvailable();
    }
  }

  protected abstract int getLevel(ActorTask actorTask);

  public String getGroupName() {
    return groupName;
  }

  public int getNumOfThreads() {
    return numOfThreads;
  }

  public void start() {
    for (ActorThread actorThread : threads) {
      actorThread.start();
    }
  }

  @SuppressWarnings("unchecked")
  public CompletableFuture<Void> closeAsync() {
    final CompletableFuture<Void>[] terminationFutures = new CompletableFuture[numOfThreads];

    for (int i = 0; i < numOfThreads; i++) {
      try {
        terminationFutures[i] = threads[i].close();
      } catch (IllegalStateException e) {
        e.printStackTrace();
        terminationFutures[i] = CompletableFuture.completedFuture(null);
      }
    }

    return CompletableFuture.allOf(terminationFutures);
  }
}
