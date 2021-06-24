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
package io.atomix.raft.impl;

import io.atomix.raft.ElectionTimer;
import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.concurrent.ThreadContext;
import java.time.Duration;
import org.slf4j.Logger;

public class PriorityElectionTimer implements ElectionTimer {

  private Scheduled electionTimer;
  private final Duration electionTimeout;
  private final ThreadContext threadContext;
  private final Runnable triggerElection;
  private final Logger log;
  private final int initialTargetPriority;
  private final int nodePriority;
  private int targetPriority;

  public PriorityElectionTimer(
      final Duration electionTimeout,
      final ThreadContext threadContext,
      final Runnable triggerElection,
      final Logger log,
      final int initialTargetPriority,
      final int nodePriority) {
    this.electionTimeout = electionTimeout;
    this.threadContext = threadContext;
    this.triggerElection = triggerElection;
    this.log = log;
    this.initialTargetPriority = initialTargetPriority;
    this.nodePriority = nodePriority;
    targetPriority = initialTargetPriority;
  }

  @Override
  public void reset() {
    cancel();
    electionTimer = threadContext.schedule(electionTimeout, this::onElectionTimeout);
    targetPriority = initialTargetPriority;
  }

  @Override
  public void cancel() {
    if (electionTimer != null) {
      electionTimer.cancel();
    }
  }

  private void onElectionTimeout() {
    final Duration pollTimeout = electionTimeout;

    if (nodePriority >= targetPriority) {
      electionTimer =
          threadContext.schedule(
              pollTimeout,
              () -> {
                log.debug("Failed to poll a majority of the cluster in {}", pollTimeout);
                reset();
              });
      triggerElection.run();
    } else {
      log.debug(
          "Node priority {} < target priority {}. Not triggering election.",
          nodePriority,
          targetPriority);
      electionTimer =
          threadContext.schedule(
              pollTimeout,
              () -> {
                targetPriority = targetPriority - 1;
                onElectionTimeout();
              });
    }
  }
}
