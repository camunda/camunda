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
import static org.assertj.core.api.Assertions.entry;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.UserTaskLookupResponses;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskLookupRecord;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class UserTaskLookupAuthorizationTest {

  private static final String USER_TASK_ID = "userTask";
  private static final String PROCESS_ID = "processId";
  private static final Map<String, Object> VARIABLES = Map.of("applicant", "frodo");
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
          .withAuthorizationsEnabled(true)
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg -> {
                final var defaultRoles = new HashMap<>(cfg.getInitialization().getDefaultRoles());
                defaultRoles.put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
                cfg.getInitialization().setDefaultRoles(defaultRoles);
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  private final UserTaskLookupResponses responses = new UserTaskLookupResponses();
  private final AtomicLong requestIds = new AtomicLong();
  private AuthorizationHelper authorizationHelper;

  @Before
  public void before() {
    authorizationHelper = new AuthorizationHelper(engine);
    responses.install(engine.getCommandResponseWriter());
  }

  @Test
  public void shouldBeAuthorizedToLookUpUserTaskWithDefaultUser() {
    // given
    final var userTaskKey = createUserTask(process());

    // when
    final var userTask = lookup(userTaskKey, DEFAULT_USER.getUsername());

    // then
    assertReturnedWithVariables(userTask, userTaskKey);
  }

  @Test
  public void
      shouldBeAuthorizedToLookUpUserTaskWithProcessDefinitionReadUserTaskWildcardPermission() {
    // given
    final var userTaskKey = createUserTask(process());
    final var username = createUser().getUsername();
    addPermission(
        username,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.READ_USER_TASK,
        AuthorizationScope.WILDCARD);

    // when
    final var userTask = lookup(userTaskKey, username);

    // then
    assertReturnedWithVariables(userTask, userTaskKey);
  }

  @Test
  public void
      shouldBeAuthorizedToLookUpUserTaskWithProcessDefinitionReadUserTaskResourceIdPermission() {
    // given
    final var userTaskKey = createUserTask(process());
    final var username = createUser().getUsername();
    addPermission(
        username,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.READ_USER_TASK,
        AuthorizationScope.id(PROCESS_ID));

    // when
    final var userTask = lookup(userTaskKey, username);

    // then
    assertReturnedWithVariables(userTask, userTaskKey);
  }

  @Test
  public void shouldBeUnauthorizedToLookUpUserTaskWithReadUserTaskPermissionForDifferentProcess() {
    // given
    final var userTaskKey = createUserTask(process());
    final var username = createUser().getUsername();
    addPermission(
        username,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.READ_USER_TASK,
        AuthorizationScope.id("otherProcess"));

    // when
    final var rejection =
        engine.userTaskLookup().withKey(userTaskKey).expectRejection().fetch(username);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(forbiddenReason(userTaskKey, ""));
  }

  @Test
  public void shouldBeAuthorizedToLookUpUserTaskWithUserTaskReadWildcardPermission() {
    // given
    final var userTaskKey = createUserTask(process());
    final var username = createUser().getUsername();
    addPermission(
        username,
        AuthorizationResourceType.USER_TASK,
        PermissionType.READ,
        AuthorizationScope.WILDCARD);

    // when
    final var userTask = lookup(userTaskKey, username);

    // then
    assertReturnedWithVariables(userTask, userTaskKey);
  }

  @Test
  public void shouldBeAuthorizedToLookUpUserTaskWithUserTaskReadResourceIdPermission() {
    // given
    final var userTaskKey = createUserTask(process());
    final var username = createUser().getUsername();
    addPermission(
        username,
        AuthorizationResourceType.USER_TASK,
        PermissionType.READ,
        AuthorizationScope.id(String.valueOf(userTaskKey)));

    // when
    final var userTask = lookup(userTaskKey, username);

    // then
    assertReturnedWithVariables(userTask, userTaskKey);
  }

  @Test
  public void shouldBeAuthorizedToLookUpUserTaskWithUserTaskReadAssigneePropertyPermission() {
    // given
    final var username = createUser().getUsername();
    final var userTaskKey =
        createUserTask(process(t -> t.zeebeAssignee(username)), UserTaskIntent.ASSIGNED);
    addPermission(
        username,
        AuthorizationResourceType.USER_TASK,
        PermissionType.READ,
        AuthorizationScope.property(PROP_ASSIGNEE));

    // when
    final var userTask = lookup(userTaskKey, username);

    // then
    assertReturnedWithVariables(userTask, userTaskKey);
  }

  @Test
  public void shouldBeAuthorizedToLookUpUserTaskWithUserTaskReadCandidateUsersPropertyPermission() {
    // given
    final var username = createUser().getUsername();
    final var userTaskKey =
        createUserTask(
            process(t -> t.zeebeCandidateUsers("samwise, %s, pippin".formatted(username))));
    addPermission(
        username,
        AuthorizationResourceType.USER_TASK,
        PermissionType.READ,
        AuthorizationScope.property(PROP_CANDIDATE_USERS));

    // when
    final var userTask = lookup(userTaskKey, username);

    // then
    assertReturnedWithVariables(userTask, userTaskKey);
  }

  @Test
  public void
      shouldBeAuthorizedToLookUpUserTaskWithUserTaskReadCandidateGroupsPropertyPermission() {
    // given
    final var user = createUser();
    final var groupId = createGroupWithUser(user);
    final var userTaskKey =
        createUserTask(process(t -> t.zeebeCandidateGroups("hobbits, %s".formatted(groupId))));
    addPermission(
        user.getUsername(),
        AuthorizationResourceType.USER_TASK,
        PermissionType.READ,
        AuthorizationScope.property(PROP_CANDIDATE_GROUPS));

    // when
    final var userTask = lookup(userTaskKey, user.getUsername());

    // then
    assertReturnedWithVariables(userTask, userTaskKey);
  }

  @Test
  public void shouldBeUnauthorizedToLookUpUserTaskWithAssigneePropertyPermissionIfNotAssignee() {
    // given
    final var username = createUser().getUsername();
    final var userTaskKey =
        createUserTask(process(t -> t.zeebeAssignee("gandalf")), UserTaskIntent.ASSIGNED);
    addPermission(
        username,
        AuthorizationResourceType.USER_TASK,
        PermissionType.READ,
        AuthorizationScope.property(PROP_ASSIGNEE));

    // when
    final var rejection =
        engine.userTaskLookup().withKey(userTaskKey).expectRejection().fetch(username);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            forbiddenReason(
                userTaskKey, " or resource must match property constraints '[assignee]'"));
  }

  @Test
  public void shouldBeUnauthorizedToLookUpUserTaskWithoutPermissions() {
    // given
    final var userTaskKey = createUserTask(process());
    final var username = createUser().getUsername();

    // when
    final var rejection =
        engine.userTaskLookup().withKey(userTaskKey).expectRejection().fetch(username);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(forbiddenReason(userTaskKey, ""));
  }

  @Test
  public void shouldBeUnauthorizedToLookUpUserTaskWithOnlyUserTaskUpdatePermission() {
    // given
    final var userTaskKey = createUserTask(process());
    final var username = createUser().getUsername();
    addPermission(
        username,
        AuthorizationResourceType.USER_TASK,
        PermissionType.UPDATE,
        AuthorizationScope.WILDCARD);

    // when
    final var rejection =
        engine.userTaskLookup().withKey(userTaskKey).expectRejection().fetch(username);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(forbiddenReason(userTaskKey, ""));
  }

  @Test
  public void shouldRejectUnknownUserTaskAsNotFoundEvenWithoutPermissions() {
    // given
    final var username = createUser().getUsername();
    final long unknownUserTaskKey = 123456789L;

    // when
    final var rejection =
        engine.userTaskLookup().withKey(unknownUserTaskKey).expectRejection().fetch(username);

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectUserTaskOfBannedInstanceAsForbiddenWithoutPermissions() {
    // given
    final var userTaskKey = createUserTask(process());
    final var processInstanceKey = getProcessInstanceKey(userTaskKey);
    engine.banInstanceInNewTransaction(1, processInstanceKey);
    RecordingExporter.errorRecords().withRecordKey(processInstanceKey).await();
    final var username = createUser().getUsername();

    // when
    final var rejection =
        engine.userTaskLookup().withKey(userTaskKey).expectRejection().fetch(username);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(forbiddenReason(userTaskKey, ""));
  }

  private static String forbiddenReason(final long userTaskKey, final String propertySuffix) {
    return """
        Insufficient permissions to perform operation 'READ_USER_TASK' on resource 'PROCESS_DEFINITION', \
        required resource identifiers are one of '[*, %s]'; \
        and Insufficient permissions to perform operation 'READ' on resource 'USER_TASK', \
        required resource identifiers are one of '[*, %d]'%s"""
        .formatted(PROCESS_ID, userTaskKey, propertySuffix);
  }

  private void assertReturnedWithVariables(
      final UserTaskLookupRecord response, final long userTaskKey) {
    assertThat(response.getUserTask().getUserTaskKey()).isEqualTo(userTaskKey);
    assertThat(response.getUserTask().getVariables()).containsOnly(entry("applicant", "frodo"));
  }

  private UserTaskLookupRecord lookup(final long userTaskKey, final String username) {
    final long requestId = requestIds.incrementAndGet();
    engine.userTaskLookup().withKey(userTaskKey).withRequestId(requestId).fetch(username);
    return responses.awaitResponse(requestId);
  }

  private void addPermission(
      final String username,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final AuthorizationScope scope) {
    authorizationHelper.addPermissionsToUser(
        username, DEFAULT_USER.getUsername(), resourceType, permissionType, scope);
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
    engine.group().newGroup(groupId).withName(UUID.randomUUID().toString()).create();
    engine
        .group()
        .addEntity(groupId)
        .withEntityId(user.getUsername())
        .withEntityType(EntityType.USER)
        .add();
    return groupId;
  }

  private long createUserTask(final BpmnModelInstance process) {
    return createUserTask(process, UserTaskIntent.CREATED);
  }

  /**
   * Waits for {@code intent} because the task's properties (e.g. the assignee) are only in the
   * state once the matching transition is done, and the check reads them from there.
   */
  private long createUserTask(final BpmnModelInstance process, final UserTaskIntent intent) {
    engine.deployment().withXmlResource("process.bpmn", process).deploy(DEFAULT_USER.getUsername());
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(VARIABLES)
            .create(DEFAULT_USER.getUsername());
    return RecordingExporter.userTaskRecords(intent)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getKey();
  }

  private long getProcessInstanceKey(final long userTaskKey) {
    return RecordingExporter.userTaskRecords()
        .withRecordKey(userTaskKey)
        .getFirst()
        .getValue()
        .getProcessInstanceKey();
  }

  private static BpmnModelInstance process() {
    return process(t -> {});
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
