/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.scheduled;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.scheduled.task.JobTimeoutChecker;
import io.camunda.zeebe.engine.scheduled.task.MessageTimeToLiveChecker;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import java.util.List;
import java.util.function.Supplier;

public final class ScheduledEngineTasks {

  private ScheduledEngineTasks() {}

  /** Builds and registers all {@link ScheduledEngineTask}s. */
  public static void registerScheduledTasks(
      final RecordProcessorContext context,
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final EngineConfiguration config) {
    final var manager =
        new ScheduledEngineTaskManager(context.getScheduleService(), scheduledTaskStateFactory);

    manager.register(new JobTimeoutChecker());
    manager.register(
        new MessageTimeToLiveChecker(
            config.getMessagesTtlCheckerBatchLimit(), config.getMessagesTtlCheckerInterval()));

    context.addLifecycleListeners(List.of(manager));
  }
}
