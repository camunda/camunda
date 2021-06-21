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
import java.util.Random;
import org.slf4j.Logger;

/**
 * An implementation of the default election in raft. It uses a randomized timeout to prevent
 * multiple nodes from starting the election at the same time.
 */
public class RandomizedElectionTimer implements ElectionTimer {

  private Scheduled electionTimer;
  private final Duration electionTimeout;
  private final ThreadContext threadContext;
  private final Random random;
  private final Runnable triggerElection;
  private final Logger log;

  public RandomizedElectionTimer(
      final Duration electionTimeout,
      final ThreadContext threadContext,
      final Random random,
      final Runnable triggerElection,
      final Logger log) {
    this.electionTimeout = electionTimeout;
    this.threadContext = threadContext;
    this.random = random;
    this.triggerElection = triggerElection;
    this.log = log;
  }

  @Override
  public void reset() {
    cancel();
    final Duration delay =
        electionTimeout.plus(Duration.ofMillis(random.nextInt((int) electionTimeout.toMillis())));
    electionTimer = threadContext.schedule(delay, this::onElectionTimeout);
  }

  @Override
  public void cancel() {
    if (electionTimer != null) {
      electionTimer.cancel();
      electionTimer = null;
    }
  }

  private void onElectionTimeout() {
    electionTimer =
        threadContext.schedule(
            electionTimeout,
            () -> {
              log.debug("Failed to poll a majority of the cluster in {}", electionTimeout);
              reset();
            });
    triggerElection.run();
  }
}
