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

import static org.agrona.UnsafeAccess.UNSAFE;

@SuppressWarnings("restriction")
public class ActorConditionImpl implements ActorCondition, ActorSubscription {
  private static final long TRIGGER_COUNT_OFFSET;

  private volatile long triggerCount = 0;
  private long runCount = 0;

  private final ActorJob job;
  private final String conditionName;
  private final ActorTask task;

  static {
    try {
      TRIGGER_COUNT_OFFSET =
          UNSAFE.objectFieldOffset(ActorConditionImpl.class.getDeclaredField("triggerCount"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public ActorConditionImpl(String conditionName, ActorJob job) {
    this.conditionName = conditionName;
    this.job = job;
    this.task = job.getTask();
  }

  @Override
  public void signal() {
    UNSAFE.getAndAddInt(this, TRIGGER_COUNT_OFFSET, 1);
    task.tryWakeup();
  }

  @Override
  public void onJobCompleted() {
    runCount++;
  }

  @Override
  public boolean poll() {
    return triggerCount > runCount;
  }

  @Override
  public ActorJob getJob() {
    return job;
  }

  @Override
  public boolean isRecurring() {
    return true;
  }

  @Override
  public String toString() {
    return "Condition " + conditionName;
  }

  @Override
  public void cancel() {
    task.onSubscriptionCancelled(this);
  }
}
