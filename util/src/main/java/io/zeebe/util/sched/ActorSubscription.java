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

import io.zeebe.util.sched.ActorTask.ActorLifecyclePhase;

/** Subscription to some external source of work / jobs. */
public interface ActorSubscription {
  /** returns true if the subscription should be able to trigger in the provided phase */
  default boolean triggersInPhase(ActorLifecyclePhase phase) {
    return phase == ActorLifecyclePhase.STARTED;
  }

  /** called by the {@link ActorThread} to determine whether the subscription has work available. */
  boolean poll();

  /**
   * called by the {@link ActorThread} after {@link #poll()} returned true to get the job to be run
   */
  ActorJob getJob();

  /**
   * Returns true in case the subscription is recurring (ie. after the job finished, the
   * subscription is re-created
   */
  boolean isRecurring();

  /** callback received as the job returned by {@link #getJob()} completes execution. */
  default void onJobCompleted() {
    // default is ignore, can be implemented by cyclic / recurring subscriptions
  }

  default void cancel() {
    // nothing to do
  }
}
