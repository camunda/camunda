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
package io.camunda.zeebe;

import static io.camunda.zeebe.ProcessInstanceMonitor.LoggingKeys.MONITORING_COUNT;
import static io.camunda.zeebe.ProcessInstanceMonitor.LoggingKeys.PROCESS_INSTANCE_KEY;
import static io.camunda.zeebe.ProcessInstanceMonitor.LoggingKeys.REQUEST_COUNT;
import static io.camunda.zeebe.ProcessInstanceMonitor.LoggingKeys.START_TIME;
import static io.camunda.zeebe.ProcessInstanceMonitor.MetricsDoc.ERRORS;
import static io.camunda.zeebe.ProcessInstanceMonitor.MetricsDoc.LATENCY;
import static io.camunda.zeebe.ProcessInstanceMonitor.MetricsDoc.MONITORING;

import io.camunda.zeebe.OperateClient.HttpResponseException;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.CloseHelper;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

final class ProcessInstanceMonitor implements ApiVisibilityMonitor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessInstanceMonitor.class);

  private static final Duration BACKOFF_DELAY = Duration.ofMillis(100);
  private static final int MAX_MONITORING_COUNT = 500;

  private final AtomicInteger monitoringCount = new AtomicInteger();

  private final OperateClient client;
  private final Executor executor;
  private final Timer latency;
  private final Counter errors;

  private volatile boolean isClosed = false;

  ProcessInstanceMonitor(
      final OperateClient client, final Executor executor, final MeterRegistry meterRegistry) {
    this.client = client;
    this.executor = executor;

    latency =
        Timer.builder(LATENCY.getName())
            .description(LATENCY.getDescription())
            .tag(MetricKeyName.ENTITY.asString(), EntityKeys.PROCESS_INSTANCE.asTag())
            .publishPercentileHistogram()
            .register(meterRegistry);
    errors =
        Counter.builder(ERRORS.getName())
            .description(ERRORS.getDescription())
            .tag(MetricKeyName.ENTITY.asString(), EntityKeys.PROCESS_INSTANCE.asTag())
            .register(meterRegistry);
    Gauge.builder(MONITORING.getName(), monitoringCount, AtomicInteger::get)
        .description(MONITORING.getDescription())
        .tag(MetricKeyName.ENTITY.asString(), EntityKeys.PROCESS_INSTANCE.asTag())
        .register(meterRegistry);
  }

  @Override
  public void monitor(final long processInstanceKey) {
    final var count = monitoringCount.incrementAndGet();
    if (count > MAX_MONITORING_COUNT) {
      monitoringCount.decrementAndGet();
      LOGGER
          .atTrace()
          .addKeyValue(PROCESS_INSTANCE_KEY.key(), processInstanceKey)
          .addKeyValue(MONITORING_COUNT.key(), count)
          .log("Exceeded maximum monitoring count, will skip this process instance");
      return;
    }

    final var task =
        new ProcessInstanceMonitorTask(
            processInstanceKey, Instant.now(), new SleepingIdleStrategy(BACKOFF_DELAY.toNanos()));
    executor.execute(task);
  }

  @Override
  public void close() {
    isClosed = true;
    CloseHelper.quietClose(client);
  }

  private final class ProcessInstanceMonitorTask implements Runnable {
    private static final Duration MONITOR_TIMEOUT = Duration.ofSeconds(60);

    private final long processInstanceKey;
    private final Instant startTime;
    private final IdleStrategy idleStrategy;

    private int requestCount;

    private ProcessInstanceMonitorTask(
        final long processInstanceKey, final Instant startTime, final IdleStrategy idleStrategy) {
      this.processInstanceKey = processInstanceKey;
      this.startTime = startTime;
      this.idleStrategy = idleStrategy;
    }

    @Override
    public void run() {
      if (isClosed) {
        LOGGER.atTrace().log("Process instance monitor is already closed, stopping task");
        return;
      }

      if (Duration.between(startTime, Instant.now()).compareTo(MONITOR_TIMEOUT) > 0) {
        withContext(LOGGER.atDebug())
            .addArgument(MONITOR_TIMEOUT)
            .log("Failed to observe process instance after {} seconds");
        latency.record(MONITOR_TIMEOUT);
        return;
      }

      final var requestTime = Instant.now();
      client
          .getProcessInstance(processInstanceKey)
          .whenCompleteAsync((r, e) -> onProcessInstanceResponse(requestTime, e), executor);
    }

    private void onProcessInstanceResponse(final Instant requestTime, final Throwable error) {
      final TaskState taskState = error != null ? handleError(error) : handleSuccess(requestTime);

      if (taskState == TaskState.STOP) {
        monitoringCount.decrementAndGet();
        return;
      }

      idleStrategy.idle(0);
      requestCount++;
      withContext(LOGGER.atTrace()).log("Retrying process instance monitoring...");
      executor.execute(this);
    }

    private TaskState handleSuccess(final Instant requestTime) {
      // subtract at least one backoff delay to account for a possible error margin of having waited
      // up to BACKOFF_DELAY too long in the last cycle
      final var piLatency = Duration.between(startTime, requestTime).minus(BACKOFF_DELAY);
      latency.record(piLatency);
      withContext(LOGGER.atDebug())
          .addArgument(piLatency)
          .log("Process instance was visible after {}");

      return TaskState.STOP;
    }

    private TaskState handleError(final Throwable error) {
      withContext(LOGGER.atTrace())
          .setCause(error)
          .log("Failed to search process instances to measure visibility latency");

      // we expect 404 (not found) until it is; however, for other errors, we will likely have
      // skewed measurements, so we should stop monitoring that PI
      if (error instanceof HttpResponseException problem && problem.code() == 404) {
        return TaskState.RETRY;
      }

      withContext(LOGGER.atDebug())
          .setCause(error)
          .log("Unexpected error while monitoring PI, will ignore to avoid skewed measurements");
      errors.increment();
      return TaskState.STOP;
    }

    private LoggingEventBuilder withContext(final LoggingEventBuilder builder) {
      return builder
          .addKeyValue(PROCESS_INSTANCE_KEY.key(), processInstanceKey)
          .addKeyValue(START_TIME.key(), startTime)
          .addKeyValue(REQUEST_COUNT.key(), requestCount)
          .addKeyValue(MONITORING_COUNT.key(), monitoringCount.get());
    }

    private enum TaskState {
      STOP,
      RETRY;
    }
  }

  enum LoggingKeys {
    REQUEST_COUNT("requestCount"),
    PROCESS_INSTANCE_KEY("processInstanceKey"),
    START_TIME("startTime"),
    MONITORING_COUNT("monitoringCount");

    private final String key;

    LoggingKeys(final String key) {
      this.key = key.intern();
    }

    String key() {
      return key;
    }
  }

  @SuppressWarnings("NullableProblems")
  public enum MetricsDoc implements ExtendedMeterDocumentation {
    /** The latency between an entity being created and initially visible by the same client. */
    LATENCY {
      @Override
      public String getDescription() {
        return "The latency between an entity being created and initially visible by the same client";
      }

      @Override
      public String getName() {
        return "camunda.benchmark.api.latency";
      }

      @Override
      public Type getType() {
        return Type.TIMER;
      }
    },

    /** The number of unexpected errors detected while monitoring for visibility latency */
    ERRORS {
      @Override
      public String getDescription() {
        return "The number of unexpected errors detected while monitoring for visibility latency";
      }

      @Override
      public String getName() {
        return "camunda.benchmark.api.latency.errors";
      }

      @Override
      public Type getType() {
        return Type.COUNTER;
      }
    },

    /** The number of PI keys currently being monitored for visibility latency. */
    MONITORING {
      @Override
      public String getDescription() {
        return "The number of PI keys currently being monitored for visibility latency";
      }

      @Override
      public String getName() {
        return "camunda.benchmark.api.latency.monitoring";
      }

      @Override
      public Type getType() {
        return Type.GAUGE;
      }
    }
  }

  @SuppressWarnings("NullableProblems")
  public enum MetricKeyName implements KeyName {
    ENTITY {
      @Override
      public String asString() {
        return "entity";
      }
    }
  }

  public enum EntityKeys {
    PROCESS_INSTANCE("process-instance");

    private final String tag;

    EntityKeys(final String tag) {
      this.tag = tag.intern();
    }

    public String asTag() {
      return tag;
    }
  }
}
