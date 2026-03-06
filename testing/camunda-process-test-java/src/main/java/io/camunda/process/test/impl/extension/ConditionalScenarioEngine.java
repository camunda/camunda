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

import io.camunda.process.test.api.ConditionalScenarioActionStep;
import io.camunda.process.test.api.ConditionalScenarioConditionStep;
import io.camunda.process.test.impl.assertions.util.AwaitilityBehavior;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages conditional scenario registration, evaluation, and lifecycle. */
public class ConditionalScenarioEngine {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConditionalScenarioEngine.class);
  private static final long POLL_INTERVAL_MS = 50;
  private static final Duration CONDITION_PROBE_TIMEOUT = Duration.ZERO;
  private static final Duration CONDITION_PROBE_INTERVAL = Duration.ZERO;

  private final CopyOnWriteArrayList<Scenario> scenarios = new CopyOnWriteArrayList<>();
  private volatile Runnable contextInitializer;
  private volatile ScheduledExecutorService executor;
  private volatile ScheduledFuture<?> pollingTask;
  private int persistentScenarioCount;

  public ConditionalScenarioConditionStep when(final Runnable condition) {
    if (condition == null) {
      throw new IllegalArgumentException("Condition must not be null");
    }
    final Scenario scenario = new Scenario(condition);
    scenarios.add(scenario);
    return new ConditionStepImpl(scenario);
  }

  public void start(final Runnable contextInitializer) {
    persistentScenarioCount = scenarios.size();
    this.contextInitializer = contextInitializer;
    if (!scenarios.isEmpty()) {
      ensurePolling();
    }
  }

  public void stop() {
    stopPolling();
    if (scenarios.size() > persistentScenarioCount) {
      scenarios.subList(persistentScenarioCount, scenarios.size()).clear();
    }
    for (final Scenario scenario : scenarios) {
      scenario.resetActionPointer();
    }
  }

  public void reset() {
    stopPolling();
    scenarios.clear();
  }

  private void stopPolling() {
    if (pollingTask != null) {
      pollingTask.cancel(false);
      pollingTask = null;
    }
    if (executor != null) {
      executor.shutdown();
      try {
        executor.awaitTermination(10, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      executor = null;
    }
  }

  private void ensurePolling() {
    if (executor == null) {
      executor =
          Executors.newSingleThreadScheduledExecutor(
              r -> {
                final Thread t = new Thread(r, "conditional-scenario-engine");
                t.setDaemon(true);
                return t;
              });
    }
    if (pollingTask == null) {
      pollingTask =
          executor.scheduleWithFixedDelay(
              this::evaluateAllScenarios,
              POLL_INTERVAL_MS,
              POLL_INTERVAL_MS,
              TimeUnit.MILLISECONDS);
    }
  }

  private void evaluateAllScenarios() {
    final Runnable initializer = contextInitializer;
    if (initializer != null) {
      try {
        initializer.run();
      } catch (final Throwable t) {
        LOGGER.debug("Context initializer failed, skipping evaluation cycle", t);
        return;
      }
    }
    for (final Scenario scenario : scenarios) {
      evaluateScenario(scenario);
    }
  }

  private void evaluateScenario(final Scenario scenario) {
    AwaitilityBehavior.setConditionProbeOverrides(
        CONDITION_PROBE_TIMEOUT, CONDITION_PROBE_INTERVAL);
    try {
      scenario.condition.run();
    } catch (final Throwable t) {
      // condition not met — skip
      return;
    } finally {
      AwaitilityBehavior.clearConditionProbeOverrides();
    }

    // condition passed — fire the current action
    final Runnable action = scenario.currentAction();
    if (action == null) {
      return;
    }
    try {
      action.run();
    } catch (final Throwable t) {
      LOGGER.warn("Conditional scenario action threw an exception", t);
    }
    scenario.advanceAction();
  }

  private static final class Scenario {

    private final Runnable condition;
    private final List<Runnable> actions = new CopyOnWriteArrayList<>();
    private final AtomicInteger actionIndex = new AtomicInteger(0);

    Scenario(final Runnable condition) {
      this.condition = condition;
    }

    void addAction(final Runnable action) {
      actions.add(action);
    }

    Runnable currentAction() {
      if (actions.isEmpty()) {
        return null;
      }
      final int idx = actionIndex.get();
      return actions.get(Math.min(idx, actions.size() - 1));
    }

    void advanceAction() {
      actionIndex.getAndUpdate(idx -> Math.min(idx + 1, actions.size() - 1));
    }

    void resetActionPointer() {
      actionIndex.set(0);
    }
  }

  private final class ConditionStepImpl implements ConditionalScenarioConditionStep {

    private final Scenario scenario;

    ConditionStepImpl(final Scenario scenario) {
      this.scenario = scenario;
    }

    @Override
    public ConditionalScenarioActionStep then(final Runnable action) {
      if (action == null) {
        throw new IllegalArgumentException("Action must not be null");
      }
      scenario.addAction(action);
      ensurePolling();
      return new ActionStepImpl(scenario);
    }
  }

  private final class ActionStepImpl implements ConditionalScenarioActionStep {

    private final Scenario scenario;

    ActionStepImpl(final Scenario scenario) {
      this.scenario = scenario;
    }

    @Override
    public ConditionalScenarioActionStep then(final Runnable action) {
      if (action == null) {
        throw new IllegalArgumentException("Action must not be null");
      }
      scenario.addAction(action);
      return this;
    }

    @Override
    public ConditionalScenarioConditionStep when(final Runnable condition) {
      return ConditionalScenarioEngine.this.when(condition);
    }
  }
}
