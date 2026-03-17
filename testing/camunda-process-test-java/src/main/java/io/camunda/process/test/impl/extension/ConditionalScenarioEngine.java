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
import io.camunda.process.test.api.ScenarioCondition;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background engine that evaluates conditional scenarios and fires their actions.
 *
 * <p>Scenarios are registered via the fluent {@link #when(ScenarioCondition)} API. Each scenario is
 * scheduled as an independent task on a {@link ScheduledThreadPoolExecutor} via {@link
 * ScheduledThreadPoolExecutor#scheduleWithFixedDelay}. On each iteration, the condition is checked
 * once (instant probe); if met, the associated action is fired, followed by a tight polling loop
 * ({@code waitForConditionReset}) that waits for the condition to reset before re-arming. This
 * prevents double-firing caused by eventual consistency in the search API.
 *
 * <p>All scenarios are ephemeral — they are cleared after each test. Lifecycle is managed by the
 * test framework through {@link #start} and {@link #stop}:
 *
 * <ul>
 *   <li>{@code start} — called before each test; sets the context initializer for thread-local
 *       propagation and the evaluation scope.
 *   <li>{@code stop} — called after each test; stops the executor and clears all scenarios.
 * </ul>
 */
public class ConditionalScenarioEngine {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConditionalScenarioEngine.class);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration RESET_TIMEOUT = Duration.ofSeconds(5);
  private static final int DEFAULT_MAX_SCENARIO_THREADS = 8;

  private final CopyOnWriteArrayList<Scenario> scenarios = new CopyOnWriteArrayList<>();

  private volatile boolean stopped = false;

  private volatile Runnable contextInitializer;
  private volatile ScenarioEvaluationScope evaluationScope = Runnable::run;
  private ScheduledThreadPoolExecutor executor;

  /**
   * Registers a new conditional scenario. The condition is evaluated periodically on a background
   * thread; when it completes without throwing, the associated action (defined via the returned
   * step) is fired.
   *
   * @param condition a runnable that throws to signal the condition is not met
   * @return the next step for defining the action
   * @throws IllegalArgumentException if condition is null
   */
  public ConditionalScenarioConditionStep when(final ScenarioCondition condition) {
    if (condition == null) {
      throw new IllegalArgumentException("Condition must not be null");
    }
    final Scenario scenario = new Scenario(condition, "#" + (scenarios.size() + 1));
    scenarios.add(scenario);
    LOGGER.trace("Registered scenario '{}'", scenario.name);
    return new ConditionStepImpl(scenario);
  }

  /**
   * Activates the engine for a test.
   *
   * @param contextInitializer a runnable executed at the start of each iteration to initialize
   *     thread-local state (e.g. {@code CamundaAssert.DATA_SOURCE}) on the scenario thread
   * @param evaluationScope a scope that wraps the condition check and action firing for each
   *     iteration with the appropriate context, such as an instant-probe await behavior override
   */
  public void start(
      final Runnable contextInitializer, final ScenarioEvaluationScope evaluationScope) {
    this.contextInitializer = contextInitializer;
    this.evaluationScope = evaluationScope;
    stopped = false;
  }

  /** Deactivates the engine after a test. Stops the executor and clears all scenarios. */
  public void stop() {
    stopped = true;
    stopExecutor();
    logSummary();
    scenarios.clear();
  }

  private void logSummary() {
    if (scenarios.isEmpty() || !LOGGER.isDebugEnabled()) {
      return;
    }
    final String details =
        scenarios.stream()
            .map(Scenario::formatSummary)
            .collect(Collectors.joining("\n  ", "\n  ", ""));
    LOGGER.debug("Scenario engine summary — {} scenario(s):{}", scenarios.size(), details);
  }

  private synchronized void stopExecutor() {
    if (executor != null) {
      executor.shutdownNow();
      try {
        if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.getSeconds(), TimeUnit.SECONDS)) {
          LOGGER.warn("Scenario executor did not terminate within {}", SHUTDOWN_TIMEOUT);
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      executor = null;
    }
  }

  private synchronized void ensureExecutor() {
    if (executor == null) {
      final ScheduledThreadPoolExecutor pool =
          new ScheduledThreadPoolExecutor(
              DEFAULT_MAX_SCENARIO_THREADS,
              new ThreadFactory() {
                private final AtomicInteger threadCount = new AtomicInteger(0);

                @Override
                public Thread newThread(final Runnable r) {
                  final Thread t =
                      new Thread(r, "conditional-scenario-" + threadCount.incrementAndGet());
                  t.setDaemon(true);
                  return t;
                }
              });
      pool.setKeepAliveTime(60, TimeUnit.SECONDS);
      pool.allowCoreThreadTimeOut(true);
      executor = pool;
      LOGGER.trace(
          "Executor created with core pool size {} and poll interval {}",
          DEFAULT_MAX_SCENARIO_THREADS,
          POLL_INTERVAL);
    }
  }

  private synchronized void scheduleScenario(final Scenario scenario) {
    ensureExecutor();
    if (executor.isShutdown()) {
      return;
    }
    executor.scheduleWithFixedDelay(
        () -> {
          try {
            evaluateScenario(scenario);
          } catch (final Throwable t) {
            LOGGER.error("Scenario '{}' evaluation failed unexpectedly", scenario.name, t);
          }
        },
        POLL_INTERVAL.toMillis(),
        POLL_INTERVAL.toMillis(),
        TimeUnit.MILLISECONDS);
    LOGGER.trace("Scheduled scenario '{}'", scenario.name);
  }

  private void evaluateScenario(final Scenario scenario) {
    if (stopped) {
      return;
    }
    final Runnable initializer = contextInitializer;
    if (initializer != null) {
      try {
        initializer.run();
      } catch (final Throwable t) {
        LOGGER.debug("Context initializer failed for '{}', skipping iteration", scenario.name, t);
        return;
      }
    }
    evaluationScope.execute(scenario::evaluate);
  }

  private static final class Scenario {

    private final ScenarioCondition condition;
    private final List<Runnable> actions = new CopyOnWriteArrayList<>();
    private final AtomicInteger actionIndex = new AtomicInteger(0);
    private final AtomicInteger fireCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile String name;

    Scenario(final ScenarioCondition condition, final String defaultName) {
      this.condition = condition;
      name = defaultName;
    }

    void addAction(final Runnable action) {
      actions.add(action);
    }

    void evaluate() {
      if (isConditionMet()) {
        LOGGER.trace("Condition met for '{}', firing action at index {}", name, actionIndex.get());
        fireAction();
        waitForConditionReset();
      }
    }

    private boolean isConditionMet() {
      try {
        condition.verifyCondition();
        return true;
      } catch (final AssertionError e) {
        return false;
      } catch (final Throwable t) {
        LOGGER.warn(
            "Scenario '{}' condition threw an unexpected exception"
                + " (expected AssertionError for unmet conditions)",
            name,
            t);
        return false;
      }
    }

    private void waitForConditionReset() {
      final long deadline = System.currentTimeMillis() + RESET_TIMEOUT.toMillis();
      while (!Thread.currentThread().isInterrupted()) {
        if (!isConditionMet()) {
          LOGGER.trace("Condition reset detected for '{}'", name);
          return;
        }
        if (System.currentTimeMillis() >= deadline) {
          LOGGER.trace("Reset timeout ({}) reached for '{}', re-arming", RESET_TIMEOUT, name);
          return;
        }
        try {
          Thread.sleep(ConditionalScenarioEngine.POLL_INTERVAL.toMillis());
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }

    private void fireAction() {
      if (actions.isEmpty()) {
        return;
      }
      fireCount.incrementAndGet();
      final Runnable action = actions.get(clampToLastAction(actionIndex.get()));
      try {
        action.run();
      } catch (final Throwable t) {
        failureCount.incrementAndGet();
        LOGGER.warn("Scenario '{}' action threw an exception, will retry", name, t);
        return;
      }
      actionIndex.set(clampToLastAction(actionIndex.get() + 1));
    }

    private String formatSummary() {
      final int fires = fireCount.get();
      final int failures = failureCount.get();
      if (fires == 0) {
        return "'" + name + "': never fired";
      }
      return failures == 0
          ? "'" + name + "': fired " + fires + " time(s)"
          : "'" + name + "': fired " + fires + " time(s), " + failures + " failed";
    }

    private int clampToLastAction(final int index) {
      return Math.min(index, actions.size() - 1);
    }
  }

  private final class ConditionStepImpl implements ConditionalScenarioConditionStep {

    private final Scenario scenario;

    ConditionStepImpl(final Scenario scenario) {
      this.scenario = scenario;
    }

    @Override
    public ConditionalScenarioConditionStep as(final String name) {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Name must not be null or blank");
      }
      scenario.name = name;
      LOGGER.trace("Named scenario '{}'", name);
      return this;
    }

    @Override
    public ConditionalScenarioActionStep then(final Runnable action) {
      if (action == null) {
        throw new IllegalArgumentException("Action must not be null");
      }
      scenario.addAction(action);
      // Scheduling starts here, before additional .then() actions may be chained. This is safe
      // because: (1) the initial delay of POLL_INTERVAL gives the test thread time to complete
      // the chain, and (2) actions use CopyOnWriteArrayList with clampToLastAction, so even if
      // the first evaluation runs early, it fires the only available action without skipping any.
      scheduleScenario(scenario);
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
    public ConditionalScenarioConditionStep when(final ScenarioCondition condition) {
      return ConditionalScenarioEngine.this.when(condition);
    }
  }
}
