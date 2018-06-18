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

public class BlockingPollSubscription implements ActorSubscription, Runnable {
  private final ActorJob subscriptionJob;
  private final Runnable blockingAction;
  private final ActorExecutor actorTaskExecutor;

  private volatile boolean isDone;
  private boolean isRecurring;

  public BlockingPollSubscription(
      ActorJob subscriptionJob,
      Runnable blockingAction,
      ActorExecutor actorTaskExecutor,
      boolean isRecurring) {
    this.subscriptionJob = subscriptionJob;
    this.blockingAction = blockingAction;
    this.actorTaskExecutor = actorTaskExecutor;
    this.isRecurring = isRecurring;
  }

  @Override
  public boolean poll() {
    return isDone;
  }

  @Override
  public ActorJob getJob() {
    return subscriptionJob;
  }

  @Override
  public boolean isRecurring() {
    return isRecurring;
  }

  @Override
  public void run() {
    try {
      blockingAction.run();
    } catch (Exception e) {
      e.printStackTrace();
      // TODO: what now?
    } finally {
      onBlockingActionCompleted();
    }
  }

  private void onBlockingActionCompleted() {
    isDone = true;
    subscriptionJob.getTask().tryWakeup();
  }

  @Override
  public void onJobCompleted() {
    if (isRecurring) {
      // re-submit
      submit();
    }
  }

  public void submit() {
    isDone = false;
    actorTaskExecutor.submitBlocking(this);
  }
}
