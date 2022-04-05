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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ExporterTestControllerTest {
  private final ExporterTestController controller = new ExporterTestController();

  @Test
  void shouldUpdateLastExportedRecordPosition() {
    // when
    controller.updateLastExportedRecordPosition(1);

    // then
    assertThat(controller.getPosition()).isEqualTo(1);
  }

  @Test
  void shouldUpdateLastExportedRecordPositionIfGreater() {
    // given
    controller.updateLastExportedRecordPosition(1);

    // when
    controller.updateLastExportedRecordPosition(0);

    // then
    assertThat(controller.getPosition()).isEqualTo(1);
  }

  @Test
  void shouldScheduleCancellableTask() {
    // given
    final Runnable task = () -> {};
    final var delay = Duration.ofSeconds(1);

    // when
    final var scheduledTask = controller.scheduleCancellableTask(delay, task);

    // then
    assertThat(controller.getScheduledTasks())
        .as("the task was scheduled with the right delay and task")
        .hasSize(1)
        .first()
        .asInstanceOf(InstanceOfAssertFactories.type(ExporterTestScheduledTask.class))
        .isEqualTo(scheduledTask)
        .extracting(ExporterTestScheduledTask::getDelay, ExporterTestScheduledTask::getTask)
        .containsExactly(delay, task);
  }

  @Test
  void shouldResetScheduler() {
    // given
    controller.runScheduledTasks(Duration.ofSeconds(5));
    controller.scheduleCancellableTask(Duration.ZERO, () -> {});

    // when
    controller.resetScheduledTasks();

    // then
    assertThat(controller)
        .extracting(ExporterTestController::getScheduledTasks, ExporterTestController::getLastRanAt)
        .containsExactly(Collections.emptyList(), Instant.EPOCH);
  }

  @Test
  void shouldRunScheduledTasks() {
    // given
    final List<Integer> completedTaskIds = new ArrayList<>();
    controller.scheduleCancellableTask(Duration.ofMinutes(1), () -> completedTaskIds.add(1));
    controller.scheduleCancellableTask(Duration.ofMinutes(2), () -> completedTaskIds.add(2));

    // when
    controller.runScheduledTasks(Duration.ofMinutes(1));

    // then
    assertThat(completedTaskIds).as("only the first task was completed").containsExactly(1);
  }
}
