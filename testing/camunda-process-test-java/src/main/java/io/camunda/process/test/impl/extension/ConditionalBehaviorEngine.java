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

import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.behavior.BehaviorCondition;
import io.camunda.process.test.api.behavior.ConditionalBehaviorBuilder;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background engine that evaluates conditional behaviors and fires their actions.
 *
 * <p>Behaviors are registered via the fluent {@link #when(BehaviorCondition)} API. Each behavior is
 * scheduled as an independent task on a {@link ScheduledThreadPoolExecutor} via {@link
 * ScheduledThreadPoolExecutor#scheduleWithFixedDelay}. On each iteration, the condition is checked
 * once (instant probe); if met, the associated action is fired, followed by a tight polling loop
 * ({@code waitForConditionReset}) that waits for the condition to reset before re-arming. This
 * prevents double-firing caused by eventual consistency in the search API.
 *
 * <p>All behaviors are ephemeral — they are cleared after each test. Lifecycle is managed by the
 * test framework through {@link #start} and {@link #stop}:
 *
 * <ul>
 *   <li>{@code start} — called before each test; sets the context initializer for thread-local
 *       propagation and the evaluation scope.
 *   <li>{@code stop} — called after each test; stops the executor and clears all behaviors.
 * </ul>
 */
public class ConditionalBehaviorEngine {

  static final Duration DEFAULT_RESET_TIMEOUT = Duration.ofSeconds(5);

  private static final Logger LOGGER = LoggerFactory.getLogger(ConditionalBehaviorEngine.class);
  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
  private static final int DEFAULT_MAX_BEHAVIOR_THREADS = 8;

  private volatile Duration pollInterval = CamundaAssert.DEFAULT_ASSERTION_INTERVAL;
  private final Duration resetTimeout;

  private final CopyOnWriteArrayList<ConditionalBehavior> behaviors = new CopyOnWriteArrayList<>();

  private volatile boolean stopped = false;

  private volatile Runnable contextInitializer;
  private volatile BehaviorEvaluationScope evaluationScope = Runnable::run;
  private ScheduledThreadPoolExecutor executor;

  public ConditionalBehaviorEngine() {
    this(DEFAULT_RESET_TIMEOUT);
  }

  ConditionalBehaviorEngine(final Duration resetTimeout) {
    this.resetTimeout = resetTimeout;
  }

  /**
   * Registers a new conditional behavior. The condition is evaluated periodically on a background
   * thread; when it completes without throwing, the associated action (defined via the returned
   * builder) is fired.
   *
   * @param condition a runnable that throws to signal the condition is not met
   * @return the builder for defining the action
   * @throws IllegalArgumentException if condition is null
   */
  public ConditionalBehaviorBuilder when(final BehaviorCondition condition) {
    if (condition == null) {
      throw new IllegalArgumentException("Condition must not be null");
    }
    final ConditionalBehavior behavior =
        new ConditionalBehavior(condition, "#" + (behaviors.size() + 1));
    behaviors.add(behavior);
    LOGGER.trace("Registered behavior '{}'", behavior.name);
    return new BuilderImpl(behavior);
  }

  /**
   * Activates the engine for a test.
   *
   * @param contextInitializer a runnable executed at the start of each iteration to initialize
   *     thread-local state (e.g. {@code CamundaAssert.DATA_SOURCE}) on the behavior thread
   * @param evaluationScope a scope that wraps the condition check and action firing for each
   *     iteration with the appropriate context, such as an instant-probe await behavior override
   */
  public void start(
      final Runnable contextInitializer,
      final BehaviorEvaluationScope evaluationScope,
      final Duration pollInterval) {
    this.contextInitializer = contextInitializer;
    this.evaluationScope = evaluationScope;
    this.pollInterval = pollInterval;
    stopped = false;
  }

  /** Deactivates the engine after a test. Stops the executor and clears all behaviors. */
  public void stop() {
    stopped = true;
    stopExecutor();
    behaviors.clear();
  }

  private synchronized void stopExecutor() {
    if (executor != null) {
      executor.shutdownNow();
      try {
        if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.getSeconds(), TimeUnit.SECONDS)) {
          LOGGER.warn("Behavior executor did not terminate within {}", SHUTDOWN_TIMEOUT);
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
              DEFAULT_MAX_BEHAVIOR_THREADS,
              new ThreadFactory() {
                private final AtomicInteger threadCount = new AtomicInteger(0);

                @Override
                public Thread newThread(final Runnable r) {
                  final Thread t =
                      new Thread(r, "conditional-behavior-" + threadCount.incrementAndGet());
                  t.setDaemon(true);
                  return t;
                }
              });
      pool.setKeepAliveTime(60, TimeUnit.SECONDS);
      pool.allowCoreThreadTimeOut(true);
      executor = pool;
      LOGGER.trace(
          "Executor created with core pool size {} and poll interval {}",
          DEFAULT_MAX_BEHAVIOR_THREADS,
          pollInterval);
    }
  }

  private synchronized void scheduleBehavior(final ConditionalBehavior behavior) {
    ensureExecutor();
    if (executor.isShutdown()) {
      return;
    }
    executor.scheduleWithFixedDelay(
        () -> {
          try {
            evaluateBehavior(behavior);
          } catch (final Throwable t) {
            LOGGER.error("Behavior '{}' evaluation failed unexpectedly", behavior.name, t);
          }
        },
        pollInterval.toMillis(),
        pollInterval.toMillis(),
        TimeUnit.MILLISECONDS);
    LOGGER.trace("Scheduled behavior '{}'", behavior.name);
  }

  private void evaluateBehavior(final ConditionalBehavior behavior) {
    if (stopped) {
      return;
    }
    final Runnable initializer = contextInitializer;
    if (initializer != null) {
      try {
        initializer.run();
      } catch (final Throwable t) {
        LOGGER.debug("Context initializer failed for '{}', skipping iteration", behavior.name, t);
        return;
      }
    }
    evaluationScope.execute(behavior::evaluate);
  }

  private final class ConditionalBehavior {

    private final BehaviorCondition condition;
    private final List<Runnable> actions = new CopyOnWriteArrayList<>();
    private final AtomicInteger actionIndex = new AtomicInteger(0);
    private final AtomicInteger fireCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile String name;

    ConditionalBehavior(final BehaviorCondition condition, final String defaultName) {
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
        LOGGER.warn("Behavior '{}' condition threw an unexpected exception", name, t);
        return false;
      }
    }

    private void waitForConditionReset() {
      final long deadline = System.currentTimeMillis() + resetTimeout.toMillis();
      while (!Thread.currentThread().isInterrupted()) {
        if (!isConditionMet()) {
          LOGGER.trace("Condition reset detected for '{}'", name);
          return;
        }
        if (System.currentTimeMillis() >= deadline) {
          LOGGER.trace("Reset timeout ({}) reached for '{}', re-arming", resetTimeout, name);
          return;
        }
        try {
          Thread.sleep(pollInterval.toMillis());
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
        LOGGER.warn("Behavior '{}' action threw an exception, will retry", name, t);
        return;
      }
      actionIndex.set(clampToLastAction(actionIndex.get() + 1));
    }

    private int clampToLastAction(final int index) {
      return Math.min(index, actions.size() - 1);
    }
  }

  private final class BuilderImpl implements ConditionalBehaviorBuilder {

    private final ConditionalBehavior behavior;
    private boolean scheduled = false;

    BuilderImpl(final ConditionalBehavior behavior) {
      this.behavior = behavior;
    }

    @Override
    public ConditionalBehaviorBuilder as(final String name) {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Name must not be null or blank");
      }
      behavior.name = name;
      LOGGER.trace("Named behavior '{}'", name);
      return this;
    }

    @Override
    public ConditionalBehaviorBuilder then(final Runnable action) {
      if (action == null) {
        throw new IllegalArgumentException("Action must not be null");
      }
      behavior.addAction(action);
      if (!scheduled) {
        scheduleBehavior(behavior);
        scheduled = true;
      }
      return this;
    }
  }
}
