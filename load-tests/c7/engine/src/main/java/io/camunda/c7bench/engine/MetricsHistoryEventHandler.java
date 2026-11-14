/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c7bench.engine;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import org.camunda.bpm.engine.impl.cfg.TransactionState;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.history.event.HistoricExternalTaskLogEntity;
import org.camunda.bpm.engine.impl.history.event.HistoricProcessInstanceEventEntity;
import org.camunda.bpm.engine.impl.history.event.HistoryEvent;
import org.camunda.bpm.engine.impl.history.event.HistoryEventTypes;
import org.camunda.bpm.engine.impl.history.handler.HistoryEventHandler;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;

/**
 * Derives throughput and latency metrics from the engine's history events.
 *
 * <p>Metrics are recorded only after the surrounding transaction commits, so retries after
 * optimistic locking exceptions or rollbacks are not double counted.
 *
 * <ul>
 *   <li>{@code c7_process_instances_total{event=started|completed}}
 *   <li>{@code c7_process_instance_duration_seconds}: end time minus start time, as stored in
 *       ACT_HI_PROCINST.DURATION_ (equivalent of zeebe_process_instance_execution_time)
 *   <li>{@code c7_external_tasks_total{event=created|completed|failed|deleted}}
 * </ul>
 *
 * <p>Requires history level {@code activity} or higher (default: {@code full}).
 */
public class MetricsHistoryEventHandler implements HistoryEventHandler {

  private final Counter piStarted;
  private final Counter piCompleted;
  private final Timer piDuration;
  private final Counter taskCreated;
  private final Counter taskCompleted;
  private final Counter taskFailed;
  private final Counter taskDeleted;

  public MetricsHistoryEventHandler(final MeterRegistry registry) {
    piStarted = processInstances(registry, "started");
    piCompleted = processInstances(registry, "completed");
    piDuration =
        Timer.builder("c7.process.instance.duration")
            .description("Process instance duration (end time - start time)")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(5))
            .maximumExpectedValue(Duration.ofMinutes(10))
            .register(registry);
    taskCreated = externalTasks(registry, "created");
    taskCompleted = externalTasks(registry, "completed");
    taskFailed = externalTasks(registry, "failed");
    taskDeleted = externalTasks(registry, "deleted");
  }

  private static Counter processInstances(final MeterRegistry registry, final String event) {
    return Counter.builder("c7.process.instances")
        .description("Process instance lifecycle events (committed)")
        .tag("event", event)
        .register(registry);
  }

  private static Counter externalTasks(final MeterRegistry registry, final String event) {
    return Counter.builder("c7.external.tasks")
        .description("External task lifecycle events (committed)")
        .tag("event", event)
        .register(registry);
  }

  @Override
  public void handleEvent(final HistoryEvent event) {
    if (event instanceof HistoricProcessInstanceEventEntity pi) {
      if (event.isEventOfType(HistoryEventTypes.PROCESS_INSTANCE_START)) {
        onCommit(piStarted::increment);
      } else if (event.isEventOfType(HistoryEventTypes.PROCESS_INSTANCE_END)) {
        // The producer does not know the start time when the instance is not cached in this
        // transaction. DbHistoryEventHandler then copies it from ACT_HI_PROCINST onto the same
        // event object, so read it at commit time.
        onCommit(
            () -> {
              piCompleted.increment();
              final Long duration = durationMillis(pi);
              if (duration != null) {
                piDuration.record(Duration.ofMillis(duration));
              }
            });
      }
    } else if (event instanceof HistoricExternalTaskLogEntity log) {
      if (log.isCreationLog()) {
        onCommit(taskCreated::increment);
      } else if (log.isSuccessLog()) {
        onCommit(taskCompleted::increment);
      } else if (log.isFailureLog()) {
        onCommit(taskFailed::increment);
      } else if (log.isDeletionLog()) {
        onCommit(taskDeleted::increment);
      }
    }
  }

  @Override
  public void handleEvents(final List<HistoryEvent> events) {
    events.forEach(this::handleEvent);
  }

  private static Long durationMillis(final HistoricProcessInstanceEventEntity pi) {
    if (pi.getDurationInMillis() != null) {
      return pi.getDurationInMillis();
    }
    if (pi.getStartTime() != null && pi.getEndTime() != null) {
      return pi.getEndTime().getTime() - pi.getStartTime().getTime();
    }
    return null;
  }

  private static void onCommit(final Runnable action) {
    final CommandContext ctx = Context.getCommandContext();
    if (ctx == null) {
      action.run();
      return;
    }
    ctx.getTransactionContext()
        .addTransactionListener(TransactionState.COMMITTED, commandContext -> action.run());
  }
}
