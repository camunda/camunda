/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UserTaskTransformerTest {

  private static final String TASK_ID = "user-task";

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage();
  private final BpmnTransformer transformer = new BpmnTransformer(expressionLanguage);

  private BpmnModelInstance processWithUserTask(final Consumer<UserTaskBuilder> userTaskModifier) {
    return Bpmn.createExecutableProcess().startEvent().userTask(TASK_ID, userTaskModifier).done();
  }

  private ExecutableJobWorkerTask transformUserTask(final BpmnModelInstance userTask) {
    final List<ExecutableProcess> processes = transformer.transformDefinitions(userTask);
    return processes.get(0).getElementById(TASK_ID, ExecutableJobWorkerTask.class);
  }

  @Nested
  class AssignmentDefinitionTests {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class AssigneeTests {

      Stream<Arguments> assignees() {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of("", null),
            Arguments.of(" ", null),
            Arguments.of("frodo", "frodo"),
            Arguments.of("=ring.bearer", "ring.bearer"));
      }

      @DisplayName("Should transform user task with assignee")
      @ParameterizedTest
      @MethodSource("assignees")
      void shouldTransform(final String assignee, final String parsedExpression) {
        final var userTask = transformUserTask(processWithUserTask(b -> b.zeebeAssignee(assignee)));
        if (parsedExpression == null) {
          assertThat(userTask.getJobWorkerProperties().getAssignee()).isNull();
        } else {
          assertThat(userTask.getJobWorkerProperties().getAssignee().getExpression())
              .isEqualTo(parsedExpression);
        }
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class CandidateGroupsTests {

      Stream<Arguments> candidateGroups() {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of("", null),
            Arguments.of(" ", null),
            Arguments.of("humans", "[\"humans\"]"),
            Arguments.of("humans,elves", "[\"humans\",\"elves\"]"),
            Arguments.of(" humans , elves ", "[\"humans\",\"elves\"]"),
            Arguments.of("=middle_earth.races", "middle_earth.races"),
            Arguments.of("=[\"elves\",\"orcs\"]", "[\"elves\",\"orcs\"]"));
      }

      @DisplayName("Should transform user task with candidateGroups")
      @ParameterizedTest
      @MethodSource("candidateGroups")
      void shouldTransform(final String candidateGroups, final String parsedExpression) {
        final var userTask =
            transformUserTask(processWithUserTask(b -> b.zeebeCandidateGroups(candidateGroups)));
        if (parsedExpression == null) {
          assertThat(userTask.getJobWorkerProperties().getCandidateGroups()).isNull();
        } else {
          assertThat(userTask.getJobWorkerProperties().getCandidateGroups().getExpression())
              .isEqualTo(parsedExpression);
        }
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class CandidateUsersTests {

      Stream<Arguments> candidateUsers() {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of("", null),
            Arguments.of(" ", null),
            Arguments.of("rose", "[\"rose\"]"),
            Arguments.of("jack,rose", "[\"jack\",\"rose\"]"),
            Arguments.of(" jack , rose ", "[\"jack\",\"rose\"]"),
            Arguments.of("=users", "users"),
            Arguments.of("=[\"jack\",\"rose\"]", "[\"jack\",\"rose\"]"));
      }

      @DisplayName("Should transform user task with candidateUsers")
      @ParameterizedTest
      @MethodSource("candidateUsers")
      void shouldTransform(final String candidateUsers, final String parsedExpression) {
        final var userTask =
            transformUserTask(processWithUserTask(b -> b.zeebeCandidateUsers(candidateUsers)));
        if (parsedExpression == null) {
          assertThat(userTask.getJobWorkerProperties().getCandidateUsers()).isNull();
        } else {
          assertThat(userTask.getJobWorkerProperties().getCandidateUsers().getExpression())
              .isEqualTo(parsedExpression);
        }
      }
    }
  }

  @Nested
  class TaskScheduleTests {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class DueDateTests {

      Stream<Arguments> dueDates() {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of("", null),
            Arguments.of(" ", null),
            Arguments.of("2023-02-27T14:35:00Z", "2023-02-27T14:35:00Z"),
            Arguments.of("2023-02-27T14:35:00+02:00", "2023-02-27T14:35:00+02:00"),
            Arguments.of(
                "2023-02-27T14:35:00+02:00[Europe/Berlin]",
                "2023-02-27T14:35:00+02:00[Europe/Berlin]"),
            Arguments.of("=dueDateSchedule", "dueDateSchedule"),
            Arguments.of("=schedule.dueDate", "schedule.dueDate"));
      }

      @DisplayName("Should transform user task with dueDate")
      @ParameterizedTest
      @MethodSource("dueDates")
      void shouldTransform(final String dueDate, final String parsedExpression) {
        final var userTask = transformUserTask(processWithUserTask(b -> b.zeebeDueDate(dueDate)));
        if (parsedExpression == null) {
          assertThat(userTask.getJobWorkerProperties().getDueDate()).isNull();
        } else {
          assertThat(userTask.getJobWorkerProperties().getDueDate().getExpression())
              .isEqualTo(parsedExpression);
        }
      }
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class FollowUpDateTests {

    Stream<Arguments> followUpDates() {
      return Stream.of(
          Arguments.of(null, null),
          Arguments.of("", null),
          Arguments.of(" ", null),
          Arguments.of("2023-02-27T14:35:00Z", "2023-02-27T14:35:00Z"),
          Arguments.of("2023-02-27T14:35:00+02:00", "2023-02-27T14:35:00+02:00"),
          Arguments.of(
              "2023-02-27T14:35:00+02:00[Europe/Berlin]",
              "2023-02-27T14:35:00+02:00[Europe/Berlin]"),
          Arguments.of("=followUpDateSchedule", "followUpDateSchedule"),
          Arguments.of("=schedule.followUpDate", "schedule.followUpDate"));
    }

    @DisplayName("Should transform user task with followUpDate")
    @ParameterizedTest
    @MethodSource("followUpDates")
    void shouldTransform(final String followUpDate, final String parsedExpression) {
      final var userTask =
          transformUserTask(processWithUserTask(b -> b.zeebeFollowUpDate(followUpDate)));
      if (parsedExpression == null) {
        assertThat(userTask.getJobWorkerProperties().getFollowUpDate()).isNull();
      } else {
        assertThat(userTask.getJobWorkerProperties().getFollowUpDate().getExpression())
            .isEqualTo(parsedExpression);
      }
    }
  }
}
