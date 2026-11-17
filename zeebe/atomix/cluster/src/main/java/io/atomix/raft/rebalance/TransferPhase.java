/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.rebalance;

/**
 * One step of a coordinated leadership transfer. A phase owns its timers and listeners, completes
 * the future its start method returned exactly once, and cleans up after itself when it completes.
 *
 * <p>Role and pause events are forwarded to the currently active phase only; each phase reacts to
 * the events that affect it and ignores the rest. All events arrive on the Raft thread.
 */
sealed interface TransferPhase permits CatchUpWait, TimeoutNowPromotion {

  /** The leader role is stopping, e.g. because this node stepped down. */
  default void onLeaderStopped() {}

  /** The transfer freeze ended. */
  default void onPauseCleared() {}
}
