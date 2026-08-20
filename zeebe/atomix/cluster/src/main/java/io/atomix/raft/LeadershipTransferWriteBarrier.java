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
 * Broker-supplied barrier that stops the partition producing new writes during a coordinated
 * leadership transfer. The barrier only covers the broker side (processing and write admission);
 * the Raft-side pause - the frozen catch-up target and the step-down watchdog - is owned by the
 * leader role and driven by the transfer itself, after the barrier is in place.
 */
public interface LeadershipTransferWriteBarrier {

  /**
   * Barrier for a server with no broker attached (e.g. Raft-only tests): there are no writes to
   * freeze.
   */
  LeadershipTransferWriteBarrier NONE =
      new LeadershipTransferWriteBarrier() {
        @Override
        public CompletableFuture<Long> freeze(final Duration timeout) {
          return CompletableFuture.completedFuture(System.currentTimeMillis());
        }

        @Override
        public CompletableFuture<Void> unfreeze() {
          return CompletableFuture.completedFuture(null);
        }
      };

  /**
   * Freezes the partition's writes for a transfer: no new entries may be produced once the returned
   * future completes. Completes with the epoch millis at which write admission was frozen, which
   * the Raft-side pause uses as the start of its resume-deadline budget. If the freeze cannot be
   * established within {@code timeout}, it must be rolled back and the future failed.
   */
  CompletableFuture<Long> freeze(Duration timeout);

  /**
   * Reopens the partition's writes after a transfer, undoing every restriction {@link #freeze}
   * applied. Must tolerate a freeze that failed or never completed.
   */
  CompletableFuture<Void> unfreeze();
}
