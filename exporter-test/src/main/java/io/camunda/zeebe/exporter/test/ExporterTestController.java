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
package io.camunda.zeebe.exporter.test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread safe implementation of {@link Controller}. Tasks are scheduled and executed
 * synchronously. To trigger execution of scheduled tasks, a manual call to {@link
 * #runScheduledTasks(Duration)} is required. Time is always relative to the last call to this
 * method.
 *
 * <p>NOTE: if a task is scheduled with a {@link Duration#ZERO}, it is <em>not</em> ran immediately,
 * but instead will run the next time {@link #runScheduledTasks(Duration)} is called.
 */
@ThreadSafe
final class ExporterTestController implements Controller {
  private static final ObjectWriter WRITER =
      new ObjectMapper().writerFor(new TypeReference<Record<?>>() {});
  private static final Logger LOGGER = LoggerFactory.getLogger(ExporterTestController.class);
  private static final long UNKNOWN_POSITION = -1;

  private final AtomicLong position = new AtomicLong(UNKNOWN_POSITION);
  private final List<ExporterTestScheduledTask> scheduledTasks = new CopyOnWriteArrayList<>();
  private final Lock schedulerLock = new ReentrantLock();
  private volatile long lastRanAtMs = 0;

  @Override
  public void updateLastExportedRecordPosition(final long position) {
    this.position.getAndAccumulate(position, Math::max);
  }

  @Override
  public ScheduledTask scheduleCancellableTask(final Duration delay, final Runnable task) {
    final var scheduledTask =
        new ExporterTestScheduledTask(
            Objects.requireNonNull(delay, "must specify a task delay"),
            Objects.requireNonNull(task, "must specify a task"));

    LockSupport.runWithLock(
        schedulerLock,
        () -> scheduleTask(scheduledTask),
        e ->
            LOGGER.warn(
                "Interrupted while acquiring schedulerLock, will not schedule new tasks", e));
    return scheduledTask;
  }

  public <T extends RecordValue> void serializeJson(
      final Record<T> record, final OutputStream output) throws IOException {
    WRITER.writeValue(output, record);
  }

  void resetScheduledTasks() {
    LockSupport.runWithLock(
        schedulerLock,
        this::resetScheduler,
        e ->
            LOGGER.warn(
                "Interrupted while acquiring schedulerLock, will not reset scheduled tasks", e));
  }

  long getPosition() {
    return position.get();
  }

  List<ExporterTestScheduledTask> getScheduledTasks() {
    return scheduledTasks;
  }

  Instant getLastRanAt() {
    return Instant.ofEpochMilli(lastRanAtMs);
  }

  /**
   * Will run all tasks scheduled since the last time this was executed + the given duration.
   *
   * @param elapsed upper bound of tasks delay
   */
  void runScheduledTasks(final Duration elapsed) {
    Objects.requireNonNull(elapsed, "must specify a tick duration");
    LockSupport.runWithLock(
        schedulerLock,
        () -> executeScheduledTasksSince(elapsed),
        e ->
            LOGGER.warn("Interrupted while awaiting executeLock, will not run scheduled tasks", e));
  }

  @GuardedBy("schedulerLock")
  private void scheduleTask(final ExporterTestScheduledTask scheduledTask) {
    scheduledTasks.add(scheduledTask);
  }

  @GuardedBy("schedulerLock")
  private void resetScheduler() {
    lastRanAtMs = 0;
    scheduledTasks.clear();
  }

  @GuardedBy("schedulerLock")
  private void executeScheduledTasksSince(final Duration elapsed) {
    final Duration upperBound = elapsed.plusMillis(lastRanAtMs);
    scheduledTasks.stream()
        .filter(t -> t.getDelay().compareTo(upperBound) <= 0)
        .sorted(Comparator.comparing(ExporterTestScheduledTask::getDelay))
        .forEach(ExporterTestScheduledTask::run);

    lastRanAtMs = upperBound.toMillis();
  }
}
