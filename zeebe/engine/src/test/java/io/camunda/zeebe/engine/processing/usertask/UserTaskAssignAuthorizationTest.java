/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static io.camunda.zeebe.engine.processing.identity.authorization.property.UserTaskAuthorizationProperties.PROP_ASSIGNEE;
import static io.camunda.zeebe.engine.processing.identity.authorization.property.UserTaskAuthorizationProperties.PROP_CANDIDATE_GROUPS;
import static io.camunda.zeebe.engine.processing.identity.authorization.property.UserTaskAuthorizationProperties.PROP_CANDIDATE_USERS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class UserTaskAssignAuthorizationTest {

  public static final String USER_TASK_ID = "userTask";
  private static final String PROCESS_ID = "processId";
  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization()
                      .getDefaultRoles()
                      .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername()))));

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();
  private AuthorizationHelper authorizationHelper;

  @Before
  public void before() {
    authorizationHelper = new AuthorizationHelper(engine);
  }

  @Test
  public void shouldBeAuthorizedToAssignUserTaskWithDefaultUser() {
    // given
    final var processInstanceKey = createProcessInstance(process());

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("assignee")
        .assign(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToAssignUserTaskWithProcessDefinitionUpdateUserTaskPermission() {
    // given
    final var processInstanceKey = createProcessInstance(process());
    final var user = createUser();
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_USER_TASK,
        AuthorizationScope.id(PROCESS_ID));

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("assignee")
        .assign(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToAssignUserTaskWithUserTaskUpdateWildcardPermission() {
    // given
    final var processInstanceKey = createProcessInstance(process());
    final var user = createUser();
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.UPDATE,
        AuthorizationScope.WILDCARD);

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("assignee")
        .assign(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToAssignUserTaskWithUserTaskUpdateResourceIdPermission() {
    // given
    final var processInstanceKey = createProcessInstance(process());
    final var user = createUser();
    final var userTaskKey = getUserTaskKey(processInstanceKey);
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.UPDATE,
        AuthorizationScope.id(String.valueOf(userTaskKey)));

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("assignee")
        .assign(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToReassignUserTaskWithUserTaskUpdateAssigneePropertyPermission() {
    // given
    final var user = createUser();
    final var processInstanceKey =
        createProcessInstance(process(t -> t.zeebeAssignee(user.getUsername())));
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.UPDATE,
        AuthorizationScope.property(PROP_ASSIGNEE));

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("newAssignee")
        .assign(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .valueFilter(task -> task.getAssignee().equals("newAssignee"))
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToUnassignUserTaskWithUserTaskUpdateAssigneePropertyPermission() {
    // given
    final var user = createUser();
    final var processInstanceKey =
        createProcessInstance(process(t -> t.zeebeAssignee(user.getUsername())));
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.UPDATE,
        AuthorizationScope.property(PROP_ASSIGNEE));

    // when
    engine.userTask().ofInstance(processInstanceKey).unassign(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .valueFilter(task -> task.getAssignee().isEmpty())
                .exists())
        .isTrue();
  }

  @Test
  public void
      shouldBeAuthorizedToAssignUserTaskWithUserTaskUpdateCandidateUsersPropertyPermission() {
    // given
    final var user = createUser();
    final var processInstanceKey =
        createProcessInstance(
            process(
                t ->
                    t.zeebeCandidateUsers(
                        "bilbo, thorin, %s, frodo".formatted(user.getUsername()))));
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.UPDATE,
        AuthorizationScope.property(PROP_CANDIDATE_USERS));

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("assignee")
        .assign(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void
      shouldBeAuthorizedToUnassignUserTaskWithUserTaskUpdateCandidateUsersPropertyPermission() {
    // given
    final var user = createUser();
    final var processInstanceKey =
        createProcessInstance(
            process(
                t -> t.zeebeCandidateUsers("dragons, balrogs, %s".formatted(user.getUsername()))));
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.UPDATE,
        AuthorizationScope.property(PROP_CANDIDATE_USERS));

    // when
    engine.userTask().ofInstance(processInstanceKey).unassign(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void
      shouldBeAuthorizedToAssignUserTaskWithUserTaskUpdateCandidateGroupsPropertyPermission() {
    // given
    final var user = createUser();
    final var groupId = createGroupWithUser(user);
    final var processInstanceKey =
        createProcessInstance(
            process(t -> t.zeebeCandidateGroups("men, %s, ents".formatted(groupId))));
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.UPDATE,
        AuthorizationScope.property(PROP_CANDIDATE_GROUPS));

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("assignee")
        .assign(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToAssignUserTaskIfNoPermissions() {
    // given
    final var processInstanceKey = createProcessInstance(process());
    final var user = createUser();
    final var userTaskKey = getUserTaskKey(processInstanceKey);

    // when
    final var rejection =
        engine
            .userTask()
            .ofInstance(processInstanceKey)
            .withAssignee("assignee")
            .expectRejection()
            .assign(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            """
                Insufficient permissions to perform operation 'UPDATE_USER_TASK' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'; \
                and Insufficient permissions to perform operation 'UPDATE' on resource 'USER_TASK', required resource identifiers are one of '[*, %s]'"""
                .formatted(PROCESS_ID, userTaskKey));
  }

  @Test
  public void
      shouldBeAuthorizedToSelfUnassignUserTaskWithProcessDefinitionClaimUserTaskWildcardPermission() {
    // given
    final var username = createUser().getUsername();
    final var processInstanceKey = createProcessInstance(process(t -> t.zeebeAssignee(username)));
    authorizationHelper.addPermissionsToUser(
        username,
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CLAIM_USER_TASK,
        AuthorizationScope.WILDCARD);

    // when
    engine.userTask().ofInstance(processInstanceKey).unassign(username);

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .valueFilter(task -> task.getAssignee().isEmpty())
                .exists())
        .isTrue();
  }

  @Test
  public void
      shouldBeAuthorizedToSelfUnassignUserTaskWithProcessDefinitionClaimUserTaskResourceIdPermission() {
    // given
    final var username = createUser().getUsername();
    final var processInstanceKey = createProcessInstance(process(t -> t.zeebeAssignee(username)));
    authorizationHelper.addPermissionsToUser(
        username,
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CLAIM_USER_TASK,
        AuthorizationScope.id(PROCESS_ID));

    // when
    engine.userTask().ofInstance(processInstanceKey).unassign(username);

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .valueFilter(task -> task.getAssignee().isEmpty())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToSelfUnassignUserTaskWithUserTaskClaimResourceIdPermission() {
    // given
    final var user = createUser();
    final var processInstanceKey =
        createProcessInstance(process(t -> t.zeebeAssignee(user.getUsername())));
    final var userTaskKey = getUserTaskKey(processInstanceKey);
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.CLAIM,
        AuthorizationScope.id(String.valueOf(userTaskKey)));

    // when
    engine.userTask().ofInstance(processInstanceKey).unassign(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .valueFilter(task -> task.getAssignee().isEmpty())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToSelfUnassignUserTaskWithUserTaskClaimWildcardPermission() {
    // given
    final var user = createUser();
    final var processInstanceKey =
        createProcessInstance(process(t -> t.zeebeAssignee(user.getUsername())));
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.CLAIM,
        AuthorizationScope.WILDCARD);

    // when - user unassigns themselves (self-unassignment)
    engine.userTask().ofInstance(processInstanceKey).unassign(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .valueFilter(task -> task.getAssignee().isEmpty())
                .exists())
        .isTrue();
  }

  @Test
  public void
      shouldBeAuthorizedToSelfUnassignUserTaskWithUserTaskClaimAssigneePropertyPermission() {
    // given
    final var user = createUser();
    final var processInstanceKey =
        createProcessInstance(process(t -> t.zeebeAssignee(user.getUsername())));
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.CLAIM,
        AuthorizationScope.property(PROP_ASSIGNEE));

    // when - user unassigns themselves (self-unassignment)
    engine.userTask().ofInstance(processInstanceKey).unassign(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .valueFilter(task -> task.getAssignee().isEmpty())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToSelfUnassignIfNotAssignedToTask() {
    // given - user is NOT assigned to the task, but another user is
    final var user = createUser();
    final var processInstanceKey =
        createProcessInstance(process(t -> t.zeebeAssignee("anotherUser")));
    final var userTaskKey = getUserTaskKey(processInstanceKey);
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.CLAIM,
        AuthorizationScope.property(PROP_ASSIGNEE));

    // when - user tries to unassign but they are not the assignee
    final var rejection =
        engine
            .userTask()
            .ofInstance(processInstanceKey)
            .expectRejection()
            .unassign(user.getUsername());

    // then - should be rejected because self-unassignment requires user to be the current assignee
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            """
                Insufficient permissions to perform operation 'UPDATE_USER_TASK' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'; \
                and Insufficient permissions to perform operation 'UPDATE' on resource 'USER_TASK', required resource identifiers are one of '[*, %s]' \
                or resource must match property constraints '[assignee]'"""
                .formatted(PROCESS_ID, userTaskKey));
  }

  @Test
  public void shouldBeUnauthorizedToSelfUnassignWithOnlyCandidateUsersPropertyPermission() {
    // given - user has CLAIM permission on candidateUsers but not assignee
    final var user = createUser();
    final var processInstanceKey =
        createProcessInstance(process(t -> t.zeebeAssignee(user.getUsername())));
    final var userTaskKey = getUserTaskKey(processInstanceKey);
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.CLAIM,
        AuthorizationScope.property(PROP_CANDIDATE_USERS));

    // when - user tries to self-unassign with only candidateUsers permission
    final var rejection =
        engine
            .userTask()
            .ofInstance(processInstanceKey)
            .expectRejection()
            .unassign(user.getUsername());

    // then - should be rejected because self-unassignment requires assignee property permission
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            """
                Insufficient permissions to perform operation 'CLAIM_USER_TASK' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'; \
                and Insufficient permissions to perform operation 'CLAIM' on resource 'USER_TASK', required resource identifiers are one of '[*, %s]' \
                or resource must match property constraints '[assignee]'"""
                .formatted(PROCESS_ID, userTaskKey));
  }

  @Test
  public void shouldBeUnauthorizedToSelfUnassignWithOnlyCandidateGroupsPropertyPermission() {
    // given - user has CLAIM permission on candidateGroups but not assignee
    final var user = createUser();
    final var groupId = createGroupWithUser(user);
    final var processInstanceKey =
        createProcessInstance(
            process(
                t ->
                    t.zeebeAssignee(user.getUsername())
                        .zeebeCandidateGroups("men, %s, ents".formatted(groupId))));
    final var userTaskKey = getUserTaskKey(processInstanceKey);
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.CLAIM,
        AuthorizationScope.property(PROP_CANDIDATE_GROUPS));

    // when - user tries to self-unassign with only candidateGroups permission
    final var rejection =
        engine
            .userTask()
            .ofInstance(processInstanceKey)
            .expectRejection()
            .unassign(user.getUsername());

    // then - should be rejected because self-unassignment requires assignee property permission
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            """
                Insufficient permissions to perform operation 'CLAIM_USER_TASK' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'; \
                and Insufficient permissions to perform operation 'CLAIM' on resource 'USER_TASK', required resource identifiers are one of '[*, %s]' \
                or resource must match property constraints '[assignee]'"""
                .formatted(PROCESS_ID, userTaskKey));
  }

  @Test
  public void shouldBeUnauthorizedToAssignOtherUserWithOnlyClaimPermission() {
    // given - user has CLAIM permission but tries to assign someone else (not unassign)
    final var user = createUser();
    final var processInstanceKey =
        createProcessInstance(process(t -> t.zeebeAssignee(user.getUsername())));
    final var userTaskKey = getUserTaskKey(processInstanceKey);
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.CLAIM,
        AuthorizationScope.property(PROP_ASSIGNEE));

    // when - user tries to assign a different user (not self-unassignment)
    final var rejection =
        engine
            .userTask()
            .ofInstance(processInstanceKey)
            .withAssignee("anotherUser")
            .expectRejection()
            .assign(user.getUsername());

    // then - should be rejected because CLAIM permission only allows self-unassignment
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            """
                Insufficient permissions to perform operation 'UPDATE_USER_TASK' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'; \
                and Insufficient permissions to perform operation 'UPDATE' on resource 'USER_TASK', required resource identifiers are one of '[*, %s]' \
                or resource must match property constraints '[assignee]'"""
                .formatted(PROCESS_ID, userTaskKey));
  }

  private UserRecordValue createUser() {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create()
        .getValue();
  }

  private long getUserTaskKey(final long processInstanceKey) {
    return RecordingExporter.userTaskRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(USER_TASK_ID)
        .getFirst()
        .getKey();
  }

  private String createGroupWithUser(final UserRecordValue user) {
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = UUID.randomUUID().toString();
    engine.group().newGroup(groupId).withName(groupName).create();
    engine
        .group()
        .addEntity(groupId)
        .withEntityId(user.getUsername())
        .withEntityType(EntityType.USER)
        .add();
    return groupId;
  }

  private long createProcessInstance(final BpmnModelInstance process) {
    engine.deployment().withXmlResource("process.bpmn", process).deploy(DEFAULT_USER.getUsername());
    return engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(DEFAULT_USER.getUsername());
  }

  private static BpmnModelInstance process() {
    return process(b -> {});
  }

  private static BpmnModelInstance process(final Consumer<UserTaskBuilder> consumer) {
    final var builder =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask(USER_TASK_ID)
            .zeebeUserTask();
    consumer.accept(builder);
    return builder.endEvent().done();
  }
}
