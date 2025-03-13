/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.TaskListener;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.TaskListenerBuilder;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UserTaskTransformerTest {

  private static final String TASK_ID = "user-task";

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final BpmnTransformer transformer = new BpmnTransformer(expressionLanguage);

  private BpmnModelInstance processWithUserTask(final Consumer<UserTaskBuilder> userTaskModifier) {
    return Bpmn.createExecutableProcess().startEvent().userTask(TASK_ID, userTaskModifier).done();
  }

  private ExecutableJobWorkerTask transformUserTask(final BpmnModelInstance userTask) {
    final List<ExecutableProcess> processes = transformer.transformDefinitions(userTask);
    return processes.get(0).getElementById(TASK_ID, ExecutableJobWorkerTask.class);
  }

  private ExecutableUserTask transformZeebeUserTask(final BpmnModelInstance userTask) {
    final List<ExecutableProcess> processes = transformer.transformDefinitions(userTask);
    return processes.get(0).getElementById(TASK_ID, ExecutableUserTask.class);
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
            Arguments.of("frodo", "\"frodo\""),
            Arguments.of("=ring.bearer", "ring.bearer"),
            Arguments.of("12345678", "\"12345678\""));
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

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class FormIdTests {

    Stream<Arguments> formIds() {
      return Stream.of(
          Arguments.of(null, null), Arguments.of("", null), Arguments.of("form-id", "form-id"));
    }

    @DisplayName("Should transform user task with formId")
    @ParameterizedTest
    @MethodSource("formIds")
    void shouldTransform(final String formId, final String parsedExpression) {
      final var userTask = transformUserTask(processWithUserTask(b -> b.zeebeFormId(formId)));
      if (parsedExpression == null) {
        assertThat(userTask.getJobWorkerProperties().getFormId()).isNull();
      } else {
        assertThat(userTask.getJobWorkerProperties().getFormId().getExpression())
            .isEqualTo(parsedExpression);
      }
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ZeebePriorityTests {

    Stream<Arguments> priority() {
      return Stream.of(
          Arguments.of(null, "50"),
          Arguments.of("", "50"),
          Arguments.of(" ", null),
          Arguments.of("30", "30"),
          Arguments.of("=10+task_priority", "10+task_priority"));
    }

    @DisplayName("Should transform user task with priority")
    @ParameterizedTest
    @MethodSource("priority")
    void shouldTransform(final String priority, final String parsedExpression) {
      final var userTask =
          transformZeebeUserTask(
              processWithUserTask(b -> b.zeebeTaskPriority(priority).zeebeUserTask()));
      if (parsedExpression == null) {
        assertThat(userTask.getUserTaskProperties().getPriority()).isNull();
      } else {
        assertThat(userTask.getUserTaskProperties().getPriority().getExpression())
            .isEqualTo(parsedExpression);
      }
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ZeebeTaskListenerTests {

    Stream<Arguments> taskListeners() {
      return Stream.of(
          Arguments.of(
              wrap(l -> l.creating().type("myType")),
              new ExpectedTaskListener(ZeebeTaskListenerEventType.creating, "myType", "3")),
          Arguments.of(
              wrap(l -> l.updating().typeExpression("myTypeExp1").retries("8")),
              new ExpectedTaskListener(ZeebeTaskListenerEventType.updating, "myTypeExp1", "8")),
          Arguments.of(
              wrap(l -> l.assigning().type("=myTypeExp2").retries("1")),
              new ExpectedTaskListener(ZeebeTaskListenerEventType.assigning, "myTypeExp2", "1")),
          Arguments.of(
              wrap(l -> l.canceling().type("myType").retriesExpression("1+2")),
              new ExpectedTaskListener(ZeebeTaskListenerEventType.canceling, "myType", "1+2")));
    }

    @DisplayName("Should transform user task with task listener")
    @ParameterizedTest
    @MethodSource("taskListeners")
    void shouldTransform(
        final Consumer<TaskListenerBuilder> taskListenerModifier,
        final ExpectedTaskListener expected) {

      final var userTask =
          transformZeebeUserTask(
              processWithUserTask(b -> b.zeebeTaskListener(taskListenerModifier).zeebeUserTask()));

      assertThat(userTask.getTaskListeners())
          .hasSize(1)
          .first()
          .satisfies(
              listener -> {
                assertThat(listener.getEventType()).isEqualTo(expected.eventType);
                assertThat(type(listener)).isEqualTo(expected.type);
                assertThat(retries(listener)).isEqualTo(expected.retries);
              });
    }

    @Test
    void shouldTransformWithTaskHeaders() {
      final var userTask =
          transformZeebeUserTask(
              processWithUserTask(
                  b ->
                      b.zeebeTaskHeader("key", "value")
                          .zeebeTaskListener(l -> l.canceling().type("myType"))
                          .zeebeUserTask()));

      assertThat(userTask.getTaskListeners())
          .hasSize(1)
          .first()
          .satisfies(
              listener -> {
                assertThat(listener.getJobWorkerProperties().getTaskHeaders())
                    .isEqualTo(Map.of("key", "value"));
              });
    }

    @DisplayName(
        "Should transform user task with multiple task listeners and preserve listener definition order")
    @Test
    void shouldTransformMultipleTaskListenersAndPreserveListenersDefinitionOrderPerEventType() {
      final var userTask =
          transformZeebeUserTask(
              processWithUserTask(
                  b ->
                      b.zeebeTaskListener(tl -> tl.creating().type("create_1"))
                          .zeebeTaskListener(tl -> tl.updating().type("update"))
                          .zeebeTaskListener(tl -> tl.assigning().type("assignment_2"))
                          .zeebeTaskListener(tl -> tl.creating().type("create_3"))
                          .zeebeTaskListener(tl -> tl.canceling().type("cancel"))
                          .zeebeTaskListener(tl -> tl.assigning().type("assignment_1"))
                          .zeebeTaskListener(tl -> tl.creating().type("create_2"))
                          .zeebeUserTask()));

      assertThat(userTask.getTaskListeners(ZeebeTaskListenerEventType.creating))
          .extracting(this::type)
          .containsExactly("create_1", "create_3", "create_2");
      assertThat(userTask.getTaskListeners(ZeebeTaskListenerEventType.assigning))
          .extracting(this::type)
          .containsExactly("assignment_2", "assignment_1");
      assertThat(userTask.getTaskListeners(ZeebeTaskListenerEventType.updating)).hasSize(1);
      assertThat(userTask.getTaskListeners(ZeebeTaskListenerEventType.canceling)).hasSize(1);
      assertThat(userTask.getTaskListeners(ZeebeTaskListenerEventType.completing)).isEmpty();
    }

    @DisplayName(
        "Should transform user task with deprecated task listener event types and use new style")
    @Test
    @SuppressWarnings("deprecation")
    void shouldTransformDeprecatedTaskListenersAndUseNewStyle() {
      final var create = ZeebeTaskListenerEventType.create;
      final var update = ZeebeTaskListenerEventType.update;
      final var assignment = ZeebeTaskListenerEventType.assignment;
      final var complete = ZeebeTaskListenerEventType.complete;
      final var cancel = ZeebeTaskListenerEventType.cancel;
      final var userTask =
          transformZeebeUserTask(
              processWithUserTask(
                  b ->
                      b.zeebeTaskListener(tl -> tl.eventType(create).type("create"))
                          .zeebeTaskListener(tl -> tl.eventType(assignment).type("assignment"))
                          .zeebeTaskListener(tl -> tl.eventType(update).type("update"))
                          .zeebeTaskListener(tl -> tl.eventType(complete).type("complete"))
                          .zeebeTaskListener(tl -> tl.eventType(cancel).type("cancel"))
                          .zeebeUserTask()));

      assertThat(userTask.getTaskListeners(ZeebeTaskListenerEventType.creating))
          .extracting(this::type)
          .containsExactly("create");
      assertThat(userTask.getTaskListeners(ZeebeTaskListenerEventType.assigning))
          .extracting(this::type)
          .containsExactly("assignment");
      assertThat(userTask.getTaskListeners(ZeebeTaskListenerEventType.updating))
          .extracting(this::type)
          .containsExactly("update");
      assertThat(userTask.getTaskListeners(ZeebeTaskListenerEventType.completing))
          .extracting(this::type)
          .containsExactly("complete");
      assertThat(userTask.getTaskListeners(ZeebeTaskListenerEventType.canceling))
          .extracting(this::type)
          .containsExactly("cancel");
    }

    private String type(final TaskListener listener) {
      return listener.getJobWorkerProperties().getType().getExpression();
    }

    private String retries(final TaskListener listener) {
      return listener.getJobWorkerProperties().getRetries().getExpression();
    }

    private Consumer<TaskListenerBuilder> wrap(final Consumer<TaskListenerBuilder> modifier) {
      return modifier;
    }

    private record ExpectedTaskListener(
        ZeebeTaskListenerEventType eventType, String type, String retries) {}
  }
}
