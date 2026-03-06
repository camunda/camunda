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
package io.camunda.process.test.impl.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConditionalScenarioEngineTest {

  private ConditionalScenarioEngine engine;

  @BeforeEach
  void setUp() {
    engine = new ConditionalScenarioEngine();
  }

  @AfterEach
  void tearDown() {
    engine.stop();
  }

  // --- Input validation ---

  @Test
  void shouldRejectNullCondition() {
    assertThatThrownBy(() -> engine.when(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNullAction() {
    final Runnable condition = () -> {};
    assertThatThrownBy(() -> engine.when(condition).then(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNullChainedAction() {
    final Runnable condition = () -> {};
    final Runnable action = () -> {};
    assertThatThrownBy(() -> engine.when(condition).then(action).then(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // --- Core behavior ---

  @Test
  void shouldFireActionWhenConditionPasses() {
    final AtomicBoolean conditionMet = new AtomicBoolean(false);
    final AtomicInteger actionCount = new AtomicInteger(0);

    engine
        .when(
            () -> {
              if (!conditionMet.get()) {
                throw new AssertionError("not yet");
              }
            })
        .then(actionCount::incrementAndGet);

    // condition not met yet
    assertThat(actionCount.get()).isZero();

    // now condition passes
    conditionMet.set(true);

    Awaitility.await().untilAsserted(() -> assertThat(actionCount.get()).isGreaterThanOrEqualTo(1));
  }

  @Test
  void shouldNotFireActionWhenConditionFails() throws InterruptedException {
    final AtomicInteger actionCount = new AtomicInteger(0);

    engine
        .when(
            () -> {
              throw new AssertionError("always fails");
            })
        .then(actionCount::incrementAndGet);

    Thread.sleep(200);

    assertThat(actionCount.get()).isZero();
  }

  @Test
  void shouldFireActionRepeatedly() {
    final AtomicInteger actionCount = new AtomicInteger(0);

    // condition always passes
    engine.when(() -> {}).then(actionCount::incrementAndGet);

    Awaitility.await().untilAsserted(() -> assertThat(actionCount.get()).isGreaterThanOrEqualTo(3));
  }

  @Test
  void shouldFireChainedActionsInOrder() {
    final List<String> sequence = Collections.synchronizedList(new ArrayList<>());

    engine
        .when(() -> {})
        .then(() -> sequence.add("first"))
        .then(() -> sequence.add("second"))
        .then(() -> sequence.add("third"));

    Awaitility.await()
        .untilAsserted(
            () -> {
              assertThat(sequence).hasSizeGreaterThanOrEqualTo(3);
              assertThat(sequence.get(0)).isEqualTo("first");
              assertThat(sequence.get(1)).isEqualTo("second");
              assertThat(sequence.subList(2, sequence.size())).allMatch("third"::equals);
            });
  }

  @Test
  void shouldRepeatLastChainedAction() {
    final List<String> sequence = Collections.synchronizedList(new ArrayList<>());

    engine.when(() -> {}).then(() -> sequence.add("first")).then(() -> sequence.add("last"));

    Awaitility.await()
        .untilAsserted(
            () -> {
              assertThat(sequence).hasSizeGreaterThanOrEqualTo(4);
              assertThat(sequence.get(0)).isEqualTo("first");
              assertThat(sequence.subList(1, sequence.size())).allMatch("last"::equals);
            });
  }

  @Test
  void shouldHandleMultipleIndependentScenarios() {
    final AtomicInteger firstActionCount = new AtomicInteger(0);
    final AtomicInteger secondActionCount = new AtomicInteger(0);

    engine
        .when(() -> {})
        .then(firstActionCount::incrementAndGet)
        .when(() -> {})
        .then(secondActionCount::incrementAndGet);

    Awaitility.await()
        .untilAsserted(
            () -> {
              assertThat(firstActionCount.get()).isGreaterThanOrEqualTo(1);
              assertThat(secondActionCount.get()).isGreaterThanOrEqualTo(1);
            });
  }

  @Test
  void shouldKeepScenarioAliveWhenActionThrows() {
    final AtomicInteger actionCount = new AtomicInteger(0);

    engine
        .when(() -> {})
        .then(
            () -> {
              actionCount.incrementAndGet();
              throw new RuntimeException("action failed");
            });

    Awaitility.await().untilAsserted(() -> assertThat(actionCount.get()).isGreaterThanOrEqualTo(3));
  }

  @Test
  void shouldStopScenariosOnStop() throws InterruptedException {
    final AtomicInteger actionCount = new AtomicInteger(0);

    engine.when(() -> {}).then(actionCount::incrementAndGet);

    Awaitility.await().untilAsserted(() -> assertThat(actionCount.get()).isGreaterThanOrEqualTo(1));

    engine.stop();
    final int countAfterStop = actionCount.get();

    Thread.sleep(200);

    assertThat(actionCount.get()).isEqualTo(countAfterStop);
  }
}
