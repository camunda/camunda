/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Broker-supplied control the leader uses to freeze and unfreeze a partition during a coordinated
 * leadership transfer. The Raft layer receives the initiate request but cannot itself freeze
 * client/inter-partition writes or the stream processor — those live in the broker — so the broker
 * registers this control on the {@link io.atomix.raft.impl.RaftContext}. When absent (e.g.
 * Raft-only tests with no writes) the leader falls back to the Raft-side pause alone.
 */
public interface LeadershipTransferPauseControl {

  /**
   * Freezes the partition for a transfer and completes with the frozen last log index — the
   * catch-up target the desired leader must reach. Arms a watchdog that steps the leader down if it
   * is not resumed within {@code resumeTimeout}.
   */
  CompletableFuture<Long> pauseForTransfer(Duration resumeTimeout);

  /** Resumes the partition after a transfer, undoing every restriction the pause applied. */
  CompletableFuture<Void> resumeFromTransfer();
}
