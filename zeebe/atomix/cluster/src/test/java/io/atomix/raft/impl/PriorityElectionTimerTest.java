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

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.DeterministicSingleThreadContext;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PriorityElectionTimerTest {

  private final Logger log = LoggerFactory.getLogger(PriorityElectionTimerTest.class);
  private final DeterministicSingleThreadContext threadContext =
      new DeterministicSingleThreadContext(new DeterministicScheduler(), MemberId.from(""));

  @AfterEach
  void afterEach() {
    threadContext.close();
  }

  @Test
  void shouldLowerPriorityNodeEventuallyStartsAnElection() {
    // given
    final AtomicInteger triggerCount = new AtomicInteger();

    final Duration electionTimeout = Duration.ofMillis(100);
    final int targetPriority = 4;
    final PriorityElectionTimer timer =
        new PriorityElectionTimer(
            electionTimeout, threadContext, triggerCount::getAndIncrement, log, targetPriority, 1);

    // when
    timer.reset();
    for (int i = 0; i < targetPriority; i++) {
      threadContext
          .getDeterministicScheduler()
          .tick(electionTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    // then
    assertThat(triggerCount.get()).describedAs("Time is triggered once").isOne();
  }

  @Test
  void shouldHighPriorityNodeStartElectionFirst() {
    // given
    final String highPrioId = "highPrioTimer";
    final String lowPrioId = "lowPrioTimer";
    final List<String> electionOrder = new CopyOnWriteArrayList<>();

    final int targetPriority = 4;
    final Duration electionTimeout = Duration.ofMillis(100);
    final PriorityElectionTimer timerHighPrio =
        new PriorityElectionTimer(
            electionTimeout,
            threadContext,
            () -> electionOrder.add(highPrioId),
            log,
            targetPriority,
            targetPriority);

    final PriorityElectionTimer timerLowPrio =
        new PriorityElectionTimer(
            electionTimeout,
            threadContext,
            () -> electionOrder.add(lowPrioId),
            log,
            targetPriority,
            1);

    // when
    timerLowPrio.reset();
    timerHighPrio.reset();

    for (int i = 0; i < targetPriority; i++) {
      threadContext
          .getDeterministicScheduler()
          .tick(electionTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    // then
    assertThat(electionOrder)
        .as("both elections should have been triggered eventually")
        .contains(highPrioId, lowPrioId);
    assertThat(electionOrder.get(0))
        .as("the first election triggered should have been the high priority election")
        .isEqualTo(highPrioId);
  }

  @Test
  void canChangePriorityDynamically() {
    final String highPrioId = "highPrioTimer";
    final String lowPrioId = "lowPrioTimer";
    final List<String> electionOrder = new CopyOnWriteArrayList<>();

    final int targetPriority = 4;
    final Duration electionTimeout = Duration.ofMillis(100);
    final PriorityElectionTimer timerLowPrio =
        new PriorityElectionTimer(
            electionTimeout,
            threadContext,
            () -> electionOrder.add(lowPrioId),
            log,
            targetPriority, // set higher priority first
            2);

    final PriorityElectionTimer timerHighPrio =
        new PriorityElectionTimer(
            electionTimeout,
            threadContext,
            () -> electionOrder.add(highPrioId),
            log,
            targetPriority,
            1); // set lower priority first

    // when
    timerLowPrio.reset();
    timerHighPrio.reset();

    threadContext
        .getDeterministicScheduler()
        .tick(electionTimeout.toMillis(), TimeUnit.MILLISECONDS);

    timerLowPrio.setNodePriority(1);
    timerHighPrio.setNodePriority(targetPriority);

    for (int i = 0; i < targetPriority - 1; i++) {
      threadContext
          .getDeterministicScheduler()
          .tick(electionTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    // then
    assertThat(electionOrder)
        .as("both elections should have been triggered eventually")
        .contains(highPrioId, lowPrioId);
    assertThat(electionOrder.get(0))
        .as("the first election triggered should have been the high priority election")
        .isEqualTo(highPrioId);
  }
}
