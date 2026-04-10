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
import static org.awaitility.Awaitility.await;

import io.camunda.process.test.api.behavior.BehaviorCondition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConditionalBehaviorEngineTest {

  private ConditionalBehaviorEngine engine;

  @BeforeEach
  void setUp() {
    engine = new ConditionalBehaviorEngine();
  }

  /** Simulates the search API catching up by reopening the gate after a short delay. */
  private static void reopenGateAfterDelay(final AtomicBoolean gate) {
    new Thread(
            () -> {
              try {
                Thread.sleep(100);
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              gate.set(true);
            })
        .start();
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
    final BehaviorCondition condition = () -> {};
    assertThatThrownBy(() -> engine.when(condition).then(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNullChainedAction() {
    final BehaviorCondition condition = () -> {};
    final Runnable action = () -> {};
    assertThatThrownBy(() -> engine.when(condition).then(action).then(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNullName() {
    assertThatThrownBy(() -> engine.when(() -> {}).as(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectBlankName() {
    assertThatThrownBy(() -> engine.when(() -> {}).as("  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldFireNamedScenario() {
    final AtomicInteger actionCount = new AtomicInteger(0);

    engine.when(() -> {}).as("my-scenario").then(actionCount::incrementAndGet);

    await().untilAsserted(() -> assertThat(actionCount.get()).isGreaterThanOrEqualTo(1));
  }

  // --- Evaluation scope wiring ---

  @Test
  void shouldUseInjectedEvaluationScope() {
    final AtomicInteger scopeCallCount = new AtomicInteger(0);
    final AtomicInteger actionCount = new AtomicInteger(0);

    engine.start(
        () -> {},
        evaluation -> {
          scopeCallCount.incrementAndGet();
          evaluation.run();
        },
        Duration.ofMillis(100));

    engine.when(() -> {}).then(actionCount::incrementAndGet);

    await()
        .untilAsserted(
            () -> {
              assertThat(scopeCallCount.get()).isGreaterThanOrEqualTo(1);
              assertThat(actionCount.get()).isGreaterThanOrEqualTo(1);
            });
  }

  @Test
  void shouldExecuteActionsWithinEvaluationScope() {
    final AtomicBoolean scopeActive = new AtomicBoolean(false);
    final AtomicBoolean actionSawScope = new AtomicBoolean(false);

    engine.start(
        () -> {},
        evaluation -> {
          scopeActive.set(true);
          try {
            evaluation.run();
          } finally {
            scopeActive.set(false);
          }
        },
        Duration.ofMillis(100));

    engine
        .when(() -> {})
        .then(
            () -> {
              if (scopeActive.get()) {
                actionSawScope.set(true);
              }
            });

    await().untilAsserted(() -> assertThat(actionSawScope.get()).isTrue());
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

    await().untilAsserted(() -> assertThat(actionCount.get()).isGreaterThanOrEqualTo(1));
  }

  @Test
  void shouldNotFireActionWhenConditionFails() {
    final AtomicInteger actionCount = new AtomicInteger(0);

    engine
        .when(
            () -> {
              throw new AssertionError("always fails");
            })
        .then(actionCount::incrementAndGet);

    await()
        .during(200, TimeUnit.MILLISECONDS)
        .untilAsserted(() -> assertThat(actionCount.get()).isZero());
  }

  @Test
  void shouldFireActionRepeatedly() {
    final AtomicInteger actionCount = new AtomicInteger(0);
    final AtomicBoolean gate = new AtomicBoolean(true);

    engine
        .when(
            () -> {
              if (!gate.get()) {
                throw new AssertionError("gate closed");
              }
            })
        .then(
            () -> {
              actionCount.incrementAndGet();
              gate.set(false);
              reopenGateAfterDelay(gate);
            });

    await().untilAsserted(() -> assertThat(actionCount.get()).isGreaterThanOrEqualTo(3));
  }

  @Test
  void shouldFireChainedActionsInOrder() {
    final List<String> sequence = Collections.synchronizedList(new ArrayList<>());
    final AtomicBoolean gate = new AtomicBoolean(true);

    engine
        .when(
            () -> {
              if (!gate.get()) {
                throw new AssertionError("gate closed");
              }
            })
        .then(
            () -> {
              sequence.add("first");
              gate.set(false);
              reopenGateAfterDelay(gate);
            })
        .then(
            () -> {
              sequence.add("second");
              gate.set(false);
              reopenGateAfterDelay(gate);
            })
        .then(
            () -> {
              sequence.add("third");
              gate.set(false);
              reopenGateAfterDelay(gate);
            });

    await()
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
    final AtomicBoolean gate = new AtomicBoolean(true);

    engine
        .when(
            () -> {
              if (!gate.get()) {
                throw new AssertionError("gate closed");
              }
            })
        .then(
            () -> {
              sequence.add("first");
              gate.set(false);
              reopenGateAfterDelay(gate);
            })
        .then(
            () -> {
              sequence.add("last");
              gate.set(false);
              reopenGateAfterDelay(gate);
            });

    await()
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

    engine.when(() -> {}).then(firstActionCount::incrementAndGet);
    engine.when(() -> {}).then(secondActionCount::incrementAndGet);

    await()
        .untilAsserted(
            () -> {
              assertThat(firstActionCount.get()).isGreaterThanOrEqualTo(1);
              assertThat(secondActionCount.get()).isGreaterThanOrEqualTo(1);
            });
  }

  @Test
  void shouldKeepScenarioAliveWhenActionThrows() {
    final AtomicInteger actionCount = new AtomicInteger(0);
    final AtomicBoolean gate = new AtomicBoolean(true);

    engine
        .when(
            () -> {
              if (!gate.get()) {
                throw new AssertionError("gate closed");
              }
            })
        .then(
            () -> {
              actionCount.incrementAndGet();
              gate.set(false);
              reopenGateAfterDelay(gate);
              throw new RuntimeException("action failed");
            });

    await().untilAsserted(() -> assertThat(actionCount.get()).isGreaterThanOrEqualTo(3));
  }

  @Test
  void shouldRetryFailedActionWithoutAdvancingChain() {
    final AtomicInteger failCount = new AtomicInteger(2);
    final List<String> sequence = Collections.synchronizedList(new ArrayList<>());
    final AtomicBoolean gate = new AtomicBoolean(true);

    engine
        .when(
            () -> {
              if (!gate.get()) {
                throw new AssertionError("gate closed");
              }
            })
        .then(
            () -> {
              if (failCount.getAndDecrement() > 0) {
                sequence.add("fail");
                gate.set(false);
                reopenGateAfterDelay(gate);
                throw new RuntimeException("not ready yet");
              }
              sequence.add("first");
              gate.set(false);
              reopenGateAfterDelay(gate);
            })
        .then(
            () -> {
              sequence.add("second");
              gate.set(false);
              reopenGateAfterDelay(gate);
            });

    await()
        .untilAsserted(
            () -> {
              assertThat(sequence).hasSizeGreaterThanOrEqualTo(4);
              // first action fails twice, then succeeds, then second action fires
              assertThat(sequence.get(0)).isEqualTo("fail");
              assertThat(sequence.get(1)).isEqualTo("fail");
              assertThat(sequence.get(2)).isEqualTo("first");
              assertThat(sequence.get(3)).isEqualTo("second");
            });
  }

  @Test
  void shouldStopScenariosOnStop() {
    final AtomicInteger actionCount = new AtomicInteger(0);

    engine.when(() -> {}).then(actionCount::incrementAndGet);

    await().untilAsserted(() -> assertThat(actionCount.get()).isGreaterThanOrEqualTo(1));

    engine.stop();
    final int countAfterStop = actionCount.get();

    await()
        .during(200, TimeUnit.MILLISECONDS)
        .untilAsserted(() -> assertThat(actionCount.get()).isEqualTo(countAfterStop));
  }

  // --- Reset-gating behavior ---

  @Test
  void shouldNotFireActionTwiceWhenConditionRemainsTrue() {
    final AtomicInteger actionCount = new AtomicInteger(0);

    // condition always passes (simulates stale search API)
    engine.when(() -> {}).then(actionCount::incrementAndGet);

    // wait for the first firing
    await().untilAsserted(() -> assertThat(actionCount.get()).isEqualTo(1));

    // action should not fire again — reset-gating prevents it
    await()
        .during(500, TimeUnit.MILLISECONDS)
        .untilAsserted(() -> assertThat(actionCount.get()).isEqualTo(1));
  }

  @Test
  void shouldRearmAfterConditionResets() {
    final AtomicInteger actionCount = new AtomicInteger(0);
    final AtomicBoolean gate = new AtomicBoolean(true);

    engine
        .when(
            () -> {
              if (!gate.get()) {
                throw new AssertionError("gate closed");
              }
            })
        .then(
            () -> {
              actionCount.incrementAndGet();
              gate.set(false);
              reopenGateAfterDelay(gate);
            });

    await().untilAsserted(() -> assertThat(actionCount.get()).isGreaterThanOrEqualTo(2));
  }

  @Test
  void shouldRearmAfterResetTimeoutWhenConditionNeverResets() {
    // use a short reset timeout so the test completes quickly
    engine.stop();
    engine = new ConditionalBehaviorEngine(Duration.ofMillis(500));

    final AtomicInteger actionCount = new AtomicInteger(0);

    // condition always passes — reset-wait will hit timeout
    engine.when(() -> {}).then(actionCount::incrementAndGet);

    // wait for at least 2 firings (each separated by the short reset timeout)
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(actionCount.get()).isGreaterThanOrEqualTo(2));
  }
}
