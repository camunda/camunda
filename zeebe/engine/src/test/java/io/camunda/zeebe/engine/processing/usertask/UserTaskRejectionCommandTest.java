/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class UserTaskRejectionCommandTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withMultiTenancyChecksEnabled(true);

  private static final String TENANT = "custom-tenant";
  private static final String USERNAME = "tenant-user";
  private static final String USERNAME2 = "tenant-user-2";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @BeforeClass
  public static void setUp() {
    ENGINE.tenant().newTenant().withTenantId(TENANT).create();
    final var user = ENGINE.user().newUser(USERNAME).create().getValue();
    ENGINE
        .tenant()
        .addEntity(TENANT)
        .withEntityType(EntityType.USER)
        .withEntityId(user.getUsername())
        .add();
    final var user2 = ENGINE.user().newUser(USERNAME2).create().getValue();
    ENGINE
        .tenant()
        .addEntity(TENANT)
        .withEntityType(EntityType.USER)
        .withEntityId(user2.getUsername())
        .add();
  }

  @Test
  public void shouldNotEnrichCompleteRejectionWhenUserTaskAlreadyCompleted() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(TENANT).create();

    final var userTaskCreated =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // Complete the user task
    ENGINE.userTask().withKey(userTaskCreated.getKey()).complete(USERNAME);

    // Wait for the task to be completed
    RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when - try to complete the already completed user task
    final var rejectionRecord =
        ENGINE.userTask().withKey(userTaskCreated.getKey()).expectRejection().complete(USERNAME);

    // then - verify the rejection is written but without enrichment
    // (since the user task no longer exists after completion, we can't look up context)
    assertThat(rejectionRecord)
        .hasIntent(UserTaskIntent.COMPLETE)
        .hasRejectionType(RejectionType.NOT_FOUND);

    // When user task is not found, rootProcessInstanceKey defaults to -1
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs(
            "Rejection record should have default rootProcessInstanceKey when task not found")
        .isEqualTo(-1L);
  }

  @Test
  public void shouldEnrichClaimRejectionWhenUserTaskAlreadyClaimed() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(TENANT).create();

    final var userTaskCreated =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // Claim the user task (self-claim: assignee == caller)
    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee(USERNAME).claim(USERNAME);

    // Wait for the task to be claimed
    RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when - a different tenant user tries to claim the already claimed user task
    final var rejectionRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAssignee(USERNAME2)
            .expectRejection()
            .claim(USERNAME2);

    // then - verify the rejection record is enriched
    assertThat(rejectionRecord)
        .hasIntent(UserTaskIntent.CLAIM)
        .hasRejectionType(RejectionType.INVALID_STATE);

    // The rootProcessInstanceKey should be set since the task exists
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs("Rejection record should have rootProcessInstanceKey set")
        .isEqualTo(processInstanceKey);

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(TENANT);
  }

  @Test
  public void shouldEnrichRejectionWithRootProcessInstanceKeyForChildProcess() {
    // given - parent process with call activity
    final String parentProcessId = Strings.newRandomValidBpmnId();
    final String childProcessId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .userTask("childTask")
                .zeebeUserTask()
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("callActivity", ca -> ca.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).withTenantId(TENANT).create();

    // Wait for the child process instance to be created
    final var childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withBpmnProcessId(childProcessId)
            .getFirst();

    final var childProcessInstanceKey = childProcessInstance.getValue().getProcessInstanceKey();

    // Wait for the child user task to be created
    final var userTaskCreated =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(childProcessInstanceKey)
            .getFirst();

    // Claim the user task (self-claim: assignee == caller)
    ENGINE.userTask().withKey(userTaskCreated.getKey()).withAssignee(USERNAME).claim(USERNAME);

    // Wait for the task to be claimed
    RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
        .withProcessInstanceKey(childProcessInstanceKey)
        .await();

    // when - a different tenant user tries to claim the already claimed user task
    final var rejectionRecord =
        ENGINE
            .userTask()
            .withKey(userTaskCreated.getKey())
            .withAssignee(USERNAME2)
            .expectRejection()
            .claim(USERNAME2);

    // then - verify the rejection record has the ROOT process instance key (parent), not the child
    assertThat(rejectionRecord)
        .hasIntent(UserTaskIntent.CLAIM)
        .hasRejectionType(RejectionType.INVALID_STATE);

    // The rootProcessInstanceKey should be the parent (root) process instance key
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs(
            "Rejection record should have rootProcessInstanceKey set to the parent process instance")
        .isEqualTo(parentProcessInstanceKey);

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(TENANT);
  }

  @Test
  public void shouldNotEnrichRejectionWhenUserTaskNotFound() {
    // given - non-existing user task key
    final long unknownUserTaskKey = 12345L;

    // when - try to complete a non-existing user task
    final var rejectionRecord =
        ENGINE.userTask().withKey(unknownUserTaskKey).expectRejection().complete();

    // then - verify the rejection is written but without enrichment
    // (since the user task doesn't exist, we can't look up rootProcessInstanceKey)
    assertThat(rejectionRecord)
        .hasIntent(UserTaskIntent.COMPLETE)
        .hasRejectionType(RejectionType.NOT_FOUND);

    // rootProcessInstanceKey should be the default value (not set) since we couldn't look it up
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs(
            "Rejection record should have default rootProcessInstanceKey when user task not found")
        .isEqualTo(-1L);
  }

  @Test
  public void shouldNotEnrichAssignRejectionWhenUserTaskDoesNotExist() {
    // given - non-existing user task key
    final long unknownUserTaskKey = 12345L;

    // when - try to assign a non-existing user task
    final var rejectionRecord =
        ENGINE
            .userTask()
            .withKey(unknownUserTaskKey)
            .withAssignee("user1")
            .expectRejection()
            .assign();

    // then - verify the rejection is written but without enrichment
    // (since the user task doesn't exist, we can't look up context)
    assertThat(rejectionRecord)
        .hasIntent(UserTaskIntent.ASSIGN)
        .hasRejectionType(RejectionType.NOT_FOUND);

    // rootProcessInstanceKey should be the default value
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs(
            "Rejection record should have default rootProcessInstanceKey when user task not found")
        .isEqualTo(-1L);
  }

  @Test
  public void shouldNotEnrichUpdateRejectionWhenUserTaskAlreadyCompleted() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(TENANT).create();

    final var userTaskCreated =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // Complete the user task
    ENGINE.userTask().withKey(userTaskCreated.getKey()).complete(USERNAME);

    // Wait for the task to be completed
    RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when - try to update the already completed user task
    final var rejectionRecord =
        ENGINE
            .userTask()
            .withKey(userTaskCreated.getKey())
            .withCandidateUsers("newUser")
            .expectRejection()
            .update(USERNAME);

    // then - verify the rejection is written but without enrichment
    // (since the user task no longer exists after completion, we can't look up context)
    assertThat(rejectionRecord)
        .hasIntent(UserTaskIntent.UPDATE)
        .hasRejectionType(RejectionType.NOT_FOUND);

    // When user task is not found, rootProcessInstanceKey defaults to -1
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs(
            "Rejection record should have default rootProcessInstanceKey when task not found")
        .isEqualTo(-1L);
  }

  @Test
  public void shouldEnrichClaimRejectionWhenUserTaskAlreadyClaimedByDifferentUser() {
    // given - create a user task and claim it
    final String processId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(TENANT).create();

    final var userTaskCreated =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // Claim the user task first (self-claim: assignee == caller)
    ENGINE.userTask().withKey(userTaskCreated.getKey()).withAssignee(USERNAME).claim(USERNAME);

    // Wait for the task to be claimed
    RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when - a different tenant user tries to claim the already claimed task
    final var rejectionRecord =
        ENGINE
            .userTask()
            .withKey(userTaskCreated.getKey())
            .withAssignee(USERNAME2)
            .expectRejection()
            .claim(USERNAME2);

    // then - verify the rejection record is enriched (task exists, just can't be claimed again)
    assertThat(rejectionRecord)
        .hasIntent(UserTaskIntent.CLAIM)
        .hasRejectionType(RejectionType.INVALID_STATE);

    // The rootProcessInstanceKey should be set since the task exists
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs("Rejection record should have rootProcessInstanceKey set")
        .isEqualTo(processInstanceKey);

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(TENANT);
  }

  @Test
  public void shouldEnrichClaimRejectionWhenAssigneeIsEmpty() {
    // given - create a user task
    final String processId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(TENANT).create();

    final var userTaskCreated =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when - try to claim with empty assignee (should be rejected)
    final var rejectionRecord =
        ENGINE
            .userTask()
            .withKey(userTaskCreated.getKey())
            .withAssignee("")
            .expectRejection()
            .claim(USERNAME);

    // then - verify the rejection record is enriched (task exists, but assignee is empty)
    assertThat(rejectionRecord)
        .hasIntent(UserTaskIntent.CLAIM)
        .hasRejectionType(RejectionType.INVALID_STATE);

    // The rootProcessInstanceKey should be set since the task exists
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs("Rejection record should have rootProcessInstanceKey set")
        .isEqualTo(processInstanceKey);

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(TENANT);
  }
}
