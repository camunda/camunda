/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.exporter;

import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @deprecated since 1.3.0. See issue <a
 *     href="https://github.com/camunda-cloud/zeebe/issues/8143">8143</a> for more information.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public class MockController implements Controller {

  public static final long UNKNOWN_POSITION = -1;

  private final List<MockScheduledTask> scheduledTasks = new ArrayList<>();
  private long lastRanAtMs = 0;
  private long position = UNKNOWN_POSITION;

  @Override
  public void updateLastExportedRecordPosition(final long position) {
    this.position = position;
  }

  @Override
  public ScheduledTask scheduleCancellableTask(final Duration delay, final Runnable task) {
    final var scheduledTask = new MockScheduledTask(delay, task);
    scheduledTasks.add(scheduledTask);
    return scheduledTask::cancel;
  }

  public void resetScheduler() {
    lastRanAtMs = 0;
    scheduledTasks.clear();
  }

  public List<MockScheduledTask> getScheduledTasks() {
    return scheduledTasks;
  }

  public long getPosition() {
    return position;
  }

  /**
   * Will run all tasks scheduled since the last time this was executed + the given duration.
   *
   * @param elapsed upper bound of tasks delay
   */
  public void runScheduledTasks(final Duration elapsed) {
    final Duration upperBound = elapsed.plusMillis(lastRanAtMs);

    scheduledTasks.stream()
        .filter(t -> t.getDelay().compareTo(upperBound) <= 0)
        .sorted(Comparator.comparing(MockScheduledTask::getDelay))
        .forEach(MockScheduledTask::run);

    lastRanAtMs = upperBound.toMillis();
  }
}
