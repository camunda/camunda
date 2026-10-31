/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.UserTaskLookupResponses;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskLookupRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskLookupIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskLifecycleState;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class UserTaskLookupTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final UserTaskLookupResponses RESPONSES = new UserTaskLookupResponses();
  private static final AtomicLong REQUEST_IDS = new AtomicLong();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final String processId = Strings.newRandomValidBpmnId();
  private final String listenerType = Strings.newRandomValidBpmnId();

  @BeforeClass
  public static void installResponseCapture() {
    RESPONSES.install(ENGINE.getCommandResponseWriter());
  }

  @Test
  public void shouldReturnUserTaskWithItsVariables() {
    // given
    final long processInstanceKey =
        createInstance(
            process(t -> t.zeebeAssignee("demo").zeebeInput("=\"local\"", "localVar")),
            Map.of("processVar", "process"));
    final var userTask = awaitUserTask(processInstanceKey, UserTaskIntent.ASSIGNED);

    // when
    final var response = lookup(userTask.getUserTaskKey());

    // then
    assertThat(response.getLifecycleState()).isEqualTo(UserTaskLifecycleState.CREATED);
    assertThat(response.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(response.getUserTask())
        .satisfies(
            task -> {
              assertThat(task.getUserTaskKey()).isEqualTo(userTask.getUserTaskKey());
              assertThat(task.getProcessInstanceKey()).isEqualTo(processInstanceKey);
              assertThat(task.getElementInstanceKey()).isEqualTo(userTask.getElementInstanceKey());
              assertThat(task.getElementId()).isEqualTo("task");
              assertThat(task.getBpmnProcessId()).isEqualTo(processId);
              assertThat(task.getAssignee()).isEqualTo("demo");
              assertThat(task.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
              assertThat(task.getVariables())
                  .containsOnly(entry("processVar", "process"), entry("localVar", "local"));
            });
  }

  @Test
  public void shouldReturnLocalVariableOverProcessVariableOfTheSameName() {
    // given
    final long processInstanceKey =
        createInstance(
            process(t -> t.zeebeInput("=\"local\"", "shared")), Map.of("shared", "process"));
    final long userTaskKey =
        awaitUserTask(processInstanceKey, UserTaskIntent.CREATED).getUserTaskKey();

    // when
    final var response = lookup(userTaskKey);

    // then
    assertThat(response.getUserTask().getVariables()).containsOnly(entry("shared", "local"));
  }

  @Test
  public void shouldReturnOnlyRequestedVariables() {
    // given
    final long processInstanceKey =
        createInstance(process(t -> t), Map.of("wanted", "yes", "unwanted", "no"));
    final long userTaskKey =
        awaitUserTask(processInstanceKey, UserTaskIntent.CREATED).getUserTaskKey();

    // when
    final var response = lookup(userTaskKey, "wanted");

    // then
    assertThat(response.getUserTask().getVariables()).containsOnly(entry("wanted", "yes"));
  }

  @Test
  public void shouldReturnNoVariablesWhenRequestedNameIsUnknown() {
    // given
    final long processInstanceKey = createInstance(process(t -> t), Map.of("existing", "yes"));
    final long userTaskKey =
        awaitUserTask(processInstanceKey, UserTaskIntent.CREATED).getUserTaskKey();

    // when
    final var response = lookup(userTaskKey, "unknown");

    // then
    assertThat(response.getUserTask().getVariables()).isEmpty();
  }

  @Test
  public void shouldWriteFetchedEventWithoutUserTask() {
    // given
    final long processInstanceKey =
        createInstance(process(t -> t.zeebeAssignee("demo")), Map.of("var", "value"));
    final long userTaskKey =
        awaitUserTask(processInstanceKey, UserTaskIntent.ASSIGNED).getUserTaskKey();

    // when
    final var fetched =
        ENGINE
            .userTaskLookup()
            .withKey(userTaskKey)
            .withFetchVariables("var")
            // a client could send the response fields; they must not reach the FETCHED event
            .withUserTask(new UserTaskRecord().setAssignee("intruder").setElementId("fake"))
            .withRequestId(REQUEST_IDS.incrementAndGet())
            .fetch();

    // then
    Assertions.assertThat(fetched).hasIntent(UserTaskLookupIntent.FETCHED).hasKey(userTaskKey);
    assertThat(fetched.getValue().getFetchVariables()).containsExactly("var");
    assertThat(fetched.getValue().getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(fetched.getValue().getLifecycleState())
        .isEqualTo(UserTaskLifecycleState.UNSPECIFIED);
    assertThat(fetched.getValue().getUserTask())
        .satisfies(
            task -> {
              assertThat(task.getUserTaskKey()).isEqualTo(-1L);
              assertThat(task.getAssignee()).isEmpty();
              assertThat(task.getElementId()).isEmpty();
              assertThat(task.getVariables()).isEmpty();
            });
  }

  @Test
  public void shouldRejectUnknownUserTask() {
    // given
    final long unknownUserTaskKey = 123456789L;

    // when
    final var rejection =
        ENGINE.userTaskLookup().withKey(unknownUserTaskKey).expectRejection().fetch();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to fetch user task with key '%d', but no such user task was found"
                .formatted(unknownUserTaskKey));
  }

  @Test
  public void shouldRejectCompletedUserTask() {
    // given
    final long processInstanceKey = createInstance(process(t -> t), Map.of());
    final long userTaskKey =
        awaitUserTask(processInstanceKey, UserTaskIntent.CREATED).getUserTaskKey();
    ENGINE.userTask().withKey(userTaskKey).complete();
    RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETED).withRecordKey(userTaskKey).await();

    // when
    final var rejection = ENGINE.userTaskLookup().withKey(userTaskKey).expectRejection().fetch();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectUserTaskOfCanceledInstance() {
    // given
    final long processInstanceKey = createInstance(process(t -> t), Map.of());
    final long userTaskKey =
        awaitUserTask(processInstanceKey, UserTaskIntent.CREATED).getUserTaskKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();
    RecordingExporter.userTaskRecords(UserTaskIntent.CANCELED).withRecordKey(userTaskKey).await();

    // when
    final var rejection = ENGINE.userTaskLookup().withKey(userTaskKey).expectRejection().fetch();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectUserTaskOfBannedInstance() {
    // given
    final long processInstanceKey = createInstance(process(t -> t), Map.of());
    final long userTaskKey =
        awaitUserTask(processInstanceKey, UserTaskIntent.CREATED).getUserTaskKey();
    ENGINE.banInstanceInNewTransaction(1, processInstanceKey);
    RecordingExporter.errorRecords().withRecordKey(processInstanceKey).await();

    // when
    final var rejection = ENGINE.userTaskLookup().withKey(userTaskKey).expectRejection().fetch();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to process command for process instance with key '%d', but the process instance is banned due to previous errors. The process instance can't be recovered, but it can be cancelled."
                .formatted(processInstanceKey));
  }

  @Test
  public void shouldReturnCreatingUserTaskWithoutAssignee() {
    // given
    final long processInstanceKey =
        createInstance(
            process(
                t ->
                    t.zeebeAssignee("demo")
                        .zeebeTaskListener(
                            l ->
                                l.eventType(ZeebeTaskListenerEventType.creating)
                                    .type(listenerType))),
            Map.of());
    final long userTaskKey =
        awaitUserTask(processInstanceKey, UserTaskIntent.CREATING).getUserTaskKey();

    // when
    final var response = lookup(userTaskKey);

    // then
    assertThat(response.getLifecycleState()).isEqualTo(UserTaskLifecycleState.CREATING);
    assertThat(response.getUserTask().getAssignee())
        .describedAs("the assignee is set only after the task is created")
        .isEmpty();
  }

  @Test
  public void shouldReturnPreviousAssigneeWhileAssigning() {
    // given
    final long processInstanceKey =
        createInstance(
            process(
                t ->
                    t.zeebeAssignee("previous")
                        .zeebeTaskListener(
                            l ->
                                l.eventType(ZeebeTaskListenerEventType.assigning)
                                    .type(listenerType))),
            Map.of());
    // the initial assignment runs the listener too
    ENGINE.job().ofInstance(processInstanceKey).withType(listenerType).complete();
    final long userTaskKey =
        awaitUserTask(processInstanceKey, UserTaskIntent.ASSIGNED).getUserTaskKey();
    ENGINE.userTask().withKey(userTaskKey).withAssignee("next").assign();

    // when
    final var response = lookup(userTaskKey);

    // then
    assertThat(response.getLifecycleState()).isEqualTo(UserTaskLifecycleState.ASSIGNING);
    assertThat(response.getUserTask().getAssignee()).isEqualTo("previous");
  }

  @Test
  public void shouldNotReturnPendingVariableUpdate() {
    // given
    final long processInstanceKey =
        createInstance(
            process(
                t ->
                    t.zeebeTaskListener(
                        l -> l.eventType(ZeebeTaskListenerEventType.updating).type(listenerType))),
            Map.of("var", "committed"));
    final var userTask = awaitUserTask(processInstanceKey, UserTaskIntent.CREATED);
    ENGINE
        .variables()
        .ofScope(userTask.getElementInstanceKey())
        .withDocument(Map.of("var", "pending"))
        .expectUpdating()
        .update();

    // when
    final var response = lookup(userTask.getUserTaskKey());

    // then
    assertThat(response.getLifecycleState()).isEqualTo(UserTaskLifecycleState.UPDATING);
    assertThat(response.getUserTask().getVariables()).containsOnly(entry("var", "committed"));
  }

  @Test
  public void shouldNotReturnCompletionVariablesWhileCompleting() {
    // given
    final long processInstanceKey =
        createInstance(
            process(
                t ->
                    t.zeebeTaskListener(
                        l ->
                            l.eventType(ZeebeTaskListenerEventType.completing).type(listenerType))),
            Map.of("var", "committed"));
    final long userTaskKey =
        awaitUserTask(processInstanceKey, UserTaskIntent.CREATED).getUserTaskKey();
    ENGINE.userTask().withKey(userTaskKey).withVariables(Map.of("var", "pending")).complete();

    // when
    final var response = lookup(userTaskKey);

    // then
    assertThat(response.getLifecycleState()).isEqualTo(UserTaskLifecycleState.COMPLETING);
    assertThat(response.getUserTask().getVariables()).containsOnly(entry("var", "committed"));
  }

  @Test
  public void shouldSeeVariablesOfPreviousTaskWhenFetchingNextTask() {
    // given
    final long processInstanceKey =
        createInstance(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("first")
                .zeebeUserTask()
                .userTask("next")
                .zeebeUserTask()
                .endEvent()
                .done(),
            Map.of());
    final long firstTaskKey =
        awaitUserTask(processInstanceKey, UserTaskIntent.CREATED).getUserTaskKey();
    ENGINE.userTask().withKey(firstTaskKey).withVariables(Map.of("result", "done")).complete();
    final long nextTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("next")
            .getFirst()
            .getKey();

    // when
    final var response = lookup(nextTaskKey);

    // then
    assertThat(response.getUserTask().getElementId()).isEqualTo("next");
    assertThat(response.getUserTask().getVariables()).containsOnly(entry("result", "done"));
  }

  @Test
  public void shouldSeeVariablesOfPreviousTaskWhenFetchingNextTaskWhileCreating() {
    // given
    final long processInstanceKey =
        createInstance(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("first")
                .zeebeUserTask()
                .userTask("next")
                .zeebeUserTask()
                .zeebeTaskListener(
                    l -> l.eventType(ZeebeTaskListenerEventType.creating).type(listenerType))
                .endEvent()
                .done(),
            Map.of());
    final long firstTaskKey =
        awaitUserTask(processInstanceKey, UserTaskIntent.CREATED).getUserTaskKey();
    ENGINE.userTask().withKey(firstTaskKey).withVariables(Map.of("result", "done")).complete();
    final long nextTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("next")
            .getFirst()
            .getKey();

    // when
    final var response = lookup(nextTaskKey);

    // then
    assertThat(response.getLifecycleState()).isEqualTo(UserTaskLifecycleState.CREATING);
    assertThat(response.getUserTask().getVariables()).containsOnly(entry("result", "done"));
  }

  private BpmnModelInstance process(final UnaryOperator<UserTaskBuilder> userTask) {
    return userTask
        .apply(
            Bpmn.createExecutableProcess(processId).startEvent().userTask("task").zeebeUserTask())
        .endEvent()
        .done();
  }

  private long createInstance(
      final BpmnModelInstance process, final Map<String, Object> variables) {
    ENGINE.deployment().withXmlResource(process).deploy();
    return ENGINE.processInstance().ofBpmnProcessId(processId).withVariables(variables).create();
  }

  private static UserTaskRecordValue awaitUserTask(
      final long processInstanceKey, final UserTaskIntent intent) {
    return RecordingExporter.userTaskRecords(intent)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getValue();
  }

  private static UserTaskLookupRecord lookup(
      final long userTaskKey, final String... fetchVariables) {
    final long requestId = REQUEST_IDS.incrementAndGet();
    ENGINE
        .userTaskLookup()
        .withKey(userTaskKey)
        .withFetchVariables(fetchVariables)
        .withRequestId(requestId)
        .fetch();
    return RESPONSES.awaitResponse(requestId);
  }
}
