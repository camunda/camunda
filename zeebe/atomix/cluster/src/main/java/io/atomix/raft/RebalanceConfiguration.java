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
package io.atomix.raft;

import java.time.Duration;
import org.jspecify.annotations.NullMarked;

/**
 * The settings bounding one coordinated leadership transfer. Resolved once when the transfer is
 * accepted so every phase of a transfer uses the same settings.
 *
 * @param replicationLagThreshold the maximum replication lag, in bytes, the desired leader may have
 *     for the transfer to be accepted
 * @param replicationTimeout how long the partition may stay frozen waiting for the desired leader
 *     to catch up before the transfer is abandoned
 * @param maxTransferAttempts how many TimeoutNow requests the leader sends, including the first,
 *     before giving up on moving leadership
 */
@NullMarked
public record RebalanceConfiguration(
    long replicationLagThreshold, Duration replicationTimeout, int maxTransferAttempts) {

  /**
   * Headroom on top of the transfer's own budget. A leader's pause watchdog is a backstop against a
   * transfer getting stuck, so this should be sized to be unreachable during a healthy transfer.
   */
  private static final Duration PAUSE_BUDGET_SLACK = Duration.ofSeconds(5);

  /** How long the partition may stay frozen before the watchdog treats the transfer as stuck. */
  public Duration pauseBudget(final Duration heartbeatInterval) {
    return replicationTimeout
        .plus(heartbeatInterval.multipliedBy(maxTransferAttempts))
        .plus(PAUSE_BUDGET_SLACK);
  }
}
