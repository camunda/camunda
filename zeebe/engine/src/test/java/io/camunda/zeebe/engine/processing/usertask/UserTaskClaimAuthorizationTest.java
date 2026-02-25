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
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
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
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class UserTaskClaimAuthorizationTest {

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
  public void shouldBeAuthorizedToClaimUserTaskWithDefaultUser() {
    // given
    final var processInstanceKey = createProcessInstance(process());

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("assignee")
        .claim(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToClaimUserTaskWithProcessDefinitionUpdateUserTaskPermission() {
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
        .withAssignee(user.getUsername())
        .claim(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void
      shouldBeAuthorizedToClaimUserTaskWithProcessDefinitionClaimUserTaskWildcardPermission() {
    // given
    final var processInstanceKey = createProcessInstance(process());
    final var username = createUser().getUsername();
    authorizationHelper.addPermissionsToUser(
        username,
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CLAIM_USER_TASK,
        AuthorizationScope.WILDCARD);

    // when
    engine.userTask().ofInstance(processInstanceKey).withAssignee(username).claim(username);

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .valueFilter(task -> task.getAssignee().equals(username))
                .exists())
        .isTrue();
  }

  @Test
  public void
      shouldBeAuthorizedToClaimUserTaskWithProcessDefinitionClaimUserTaskResourceIdPermission() {
    // given
    final var processInstanceKey = createProcessInstance(process());
    final var username = createUser().getUsername();
    authorizationHelper.addPermissionsToUser(
        username,
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CLAIM_USER_TASK,
        AuthorizationScope.id(PROCESS_ID));

    // when
    engine.userTask().ofInstance(processInstanceKey).withAssignee(username).claim(username);

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .valueFilter(task -> task.getAssignee().equals(username))
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToClaimUserTaskWithClaimUserTaskPermissionForDifferentProcess() {
    // given
    final var processInstanceKey = createProcessInstance(process());
    final var username = createUser().getUsername();
    authorizationHelper.addPermissionsToUser(
        username,
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CLAIM_USER_TASK,
        AuthorizationScope.id("otherProcess"));

    // when
    final var rejection =
        engine
            .userTask()
            .ofInstance(processInstanceKey)
            .withAssignee(username)
            .expectRejection()
            .claim(username);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .extracting(Record::getRejectionReason, as(InstanceOfAssertFactories.STRING))
        .contains(
            """
                Insufficient permissions to perform operation 'CLAIM_USER_TASK' on resource 'PROCESS_DEFINITION', \
                required resource identifiers are one of '[*, %s]'"""
                .formatted(PROCESS_ID));
  }

  @Test
  public void shouldBeAuthorizedToClaimUserTaskWithUserTaskUpdateWildcardPermission() {
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
        .withAssignee(user.getUsername())
        .claim(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToClaimUserTaskWithUserTaskUpdateResourceIdPermission() {
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
        .withAssignee(user.getUsername())
        .claim(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToClaimUserTaskWithUserTaskUpdateAssigneePropertyPermission() {
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
        .withAssignee(user.getUsername())
        .claim(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void
      shouldBeAuthorizedToClaimUserTaskWithUserTaskUpdateCandidateUsersPropertyPermission() {
    // given
    final var user = createUser();
    final var processInstanceKey =
        createProcessInstance(
            process(
                t -> t.zeebeCandidateUsers("elrond, %s, legolas".formatted(user.getUsername()))));
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
        .withAssignee(user.getUsername())
        .claim(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void
      shouldBeAuthorizedToClaimUserTaskWithUserTaskUpdateCandidateGroupsPropertyPermission() {
    // given
    final var user = createUser();
    final var groupId = createGroupWithUser(user);
    final var processInstanceKey =
        createProcessInstance(
            process(t -> t.zeebeCandidateGroups("dwarves, %s".formatted(groupId))));
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
        .withAssignee(user.getUsername())
        .claim(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToClaimUserTaskWithUserTaskClaimWildcardPermission() {
    // given
    final var processInstanceKey = createProcessInstance(process());
    final var user = createUser();
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.CLAIM,
        AuthorizationScope.WILDCARD);

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee(user.getUsername())
        .claim(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToClaimUserTaskWithUserTaskClaimResourceIdPermission() {
    // given
    final var processInstanceKey = createProcessInstance(process());
    final var user = createUser();
    final var userTaskKey = getUserTaskKey(processInstanceKey);
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.CLAIM,
        AuthorizationScope.id(String.valueOf(userTaskKey)));

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee(user.getUsername())
        .claim(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToClaimUserTaskWithUserTaskClaimAssigneePropertyPermission() {
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

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee(user.getUsername())
        .claim(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToClaimUserTaskWithUserTaskClaimCandidateUsersPropertyPermission() {
    // given
    final var user = createUser();
    final var processInstanceKey =
        createProcessInstance(
            process(
                t -> t.zeebeCandidateUsers("faramir, %s, boromir".formatted(user.getUsername()))));
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.CLAIM,
        AuthorizationScope.property(PROP_CANDIDATE_USERS));

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee(user.getUsername())
        .claim(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void
      shouldBeAuthorizedToClaimUserTaskWithUserTaskClaimCandidateGroupsPropertyPermission() {
    // given
    final var user = createUser();
    final var groupId = createGroupWithUser(user);
    final var processInstanceKey =
        createProcessInstance(
            process(t -> t.zeebeCandidateGroups("%s, orcs, trolls".formatted(groupId))));
    authorizationHelper.addPermissionsToUser(
        user.getUsername(),
        DEFAULT_USER.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.CLAIM,
        AuthorizationScope.property(PROP_CANDIDATE_GROUPS));

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee(user.getUsername())
        .claim(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToClaimUserTaskIfNoPermissions() {
    // given
    final var processInstanceKey = createProcessInstance(process());
    final var userTaskKey = getUserTaskKey(processInstanceKey);
    final var user = createUser();

    // when
    final var rejection =
        engine
            .userTask()
            .ofInstance(processInstanceKey)
            .withAssignee("assignee")
            .expectRejection()
            .claim(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            """
                Insufficient permissions to perform operation 'UPDATE_USER_TASK' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'; \
                and Insufficient permissions to perform operation 'CLAIM_USER_TASK' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'; \
                and Insufficient permissions to perform operation 'UPDATE' on resource 'USER_TASK', required resource identifiers are one of '[*, %s]'; \
                and Insufficient permissions to perform operation 'CLAIM' on resource 'USER_TASK', required resource identifiers are one of '[*, %s]'"""
                .formatted(PROCESS_ID, PROCESS_ID, userTaskKey, userTaskKey));
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

  private long getUserTaskKey(final long processInstanceKey) {
    return RecordingExporter.userTaskRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(USER_TASK_ID)
        .getFirst()
        .getKey();
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
