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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.agrona.collections.MutableBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ExporterTestScheduledTaskTest {
  @Test
  void shouldRun() {
    // given
    final MutableBoolean wasRan = new MutableBoolean();
    final Runnable task = () -> wasRan.set(true);
    final ExporterTestScheduledTask scheduledTask =
        new ExporterTestScheduledTask(Duration.ZERO, task);

    // when
    scheduledTask.run();

    // then
    assertThat(wasRan.get()).as("wrapped task was actually ran").isTrue();
    assertThat(scheduledTask.wasExecuted()).as("task was marked as executed").isTrue();
  }

  @Test
  void shouldCancel() {
    // given
    final MutableBoolean wasRan = new MutableBoolean();
    final Runnable task = () -> wasRan.set(true);
    final ExporterTestScheduledTask scheduledTask =
        new ExporterTestScheduledTask(Duration.ZERO, task);

    // when
    scheduledTask.cancel();
    scheduledTask.run();

    // then
    assertThat(wasRan.get()).as("wrapped task was not actually ran").isFalse();
    assertThat(scheduledTask)
        .as("scheduled task was marked as cancelled and not executed")
        .extracting(ExporterTestScheduledTask::isCanceled, ExporterTestScheduledTask::wasExecuted)
        .containsExactly(true, false);
  }

  @Test
  void shouldNotCancelAfterRun() {
    // given
    final MutableBoolean wasRan = new MutableBoolean();
    final Runnable task = () -> wasRan.set(true);
    final ExporterTestScheduledTask scheduledTask =
        new ExporterTestScheduledTask(Duration.ZERO, task);

    // when
    scheduledTask.run();
    scheduledTask.cancel();

    // then
    assertThat(wasRan.get()).as("wrapped task was actually ran").isTrue();
    assertThat(scheduledTask)
        .as("scheduled task was marked as executed and not cancelled")
        .extracting(ExporterTestScheduledTask::isCanceled, ExporterTestScheduledTask::wasExecuted)
        .containsExactly(false, true);
  }
}
