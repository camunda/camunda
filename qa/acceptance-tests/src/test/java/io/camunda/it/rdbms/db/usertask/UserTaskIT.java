/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.usertask;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.generateRandomString;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromResourceIds;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromTenantIds;
import static io.camunda.it.rdbms.db.fixtures.UserTaskFixtures.createAndSaveRandomUserTasks;
import static io.camunda.it.rdbms.db.fixtures.UserTaskFixtures.createAndSaveUserTask;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.UserTaskDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.UserTaskState;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.UserTaskFixtures;
import io.camunda.it.rdbms.db.fixtures.VariableFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.sort.UserTaskSort;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.condition.AuthorizationConditions;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class UserTaskIT {

  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldCreateAndFindUserTaskByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);

    final var instance = rdbmsService.getUserTaskReader().findOne(userTask.userTaskKey()).get();
    assertUserTaskEntity(instance, userTask);
  }

  @TestTemplate
  public void shouldCreateAndFindUserTaskWithoutCandidatesByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask =
        UserTaskFixtures.createRandomized(b -> b.candidateGroups(null).candidateUsers(null));
    createAndSaveUserTask(rdbmsService, userTask);

    final var instance = rdbmsService.getUserTaskReader().findOne(userTask.userTaskKey()).get();
    assertUserTaskEntity(instance, userTask);
  }

  @TestTemplate
  public void shouldUpdateAndFindUserTaskByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);

    final UserTaskDbModel updatedModel =
        userTask.toBuilder().state(UserTaskState.COMPLETED).completionDate(NOW.plusDays(2)).build();
    final RdbmsWriters writer = rdbmsService.createWriter(1L);
    writer.getUserTaskWriter().update(updatedModel);
    writer.flush();

    final var instance = rdbmsService.getUserTaskReader().findOne(userTask.userTaskKey()).get();
    assertUserTaskEntity(instance, updatedModel);
  }

  @TestTemplate
  public void shouldUpdateAndFindUserTaskWithoutCandidatesByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask =
        UserTaskFixtures.createRandomized(b -> b.candidateGroups(null).candidateUsers(null));
    createAndSaveUserTask(rdbmsService, userTask);

    final UserTaskDbModel updatedModel =
        userTask.toBuilder()
            .state(UserTaskState.COMPLETED)
            .completionDate(NOW.plusDays(2))
            .assignee("newAssignee")
            .priority(2)
            .dueDate(NOW.plusDays(3))
            .followUpDate(NOW.plusDays(4))
            .build();
    final RdbmsWriters writer = rdbmsService.createWriter(1L);
    writer.getUserTaskWriter().update(updatedModel);
    writer.flush();

    final var instance = rdbmsService.getUserTaskReader().findOne(userTask.userTaskKey()).get();
    assertUserTaskEntity(instance, updatedModel);
  }

  @TestTemplate
  public void shouldDeletingExistingCandidates(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);

    final UserTaskDbModel updatedModel =
        userTask.toBuilder().candidateGroups(null).candidateUsers(null).build();
    final RdbmsWriters writer = rdbmsService.createWriter(1L);
    writer.getUserTaskWriter().update(updatedModel);
    writer.flush();

    final var instance = rdbmsService.getUserTaskReader().findOne(userTask.userTaskKey()).get();
    assertUserTaskEntity(instance, updatedModel);
  }

  @TestTemplate
  public void shouldUpdateCandidateUserAndGroupAndFindByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(1L);

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsWriters, userTask);

    final UserTaskDbModel updatedModel =
        userTask.toBuilder()
            .candidateUsers(List.of("user1", "user2", "user3"))
            .candidateGroups(List.of("group1", "group2"))
            .build();
    rdbmsWriters.getUserTaskWriter().update(updatedModel);
    rdbmsWriters.flush();

    final var instance = rdbmsService.getUserTaskReader().findOne(userTask.userTaskKey()).get();
    assertUserTaskEntity(instance, updatedModel);
  }

  @TestTemplate
  public void shouldFindUserTaskByProcessInstanceKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .processInstanceKeys(userTask.processInstanceKey())
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByAuthorizedResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);
    createAndSaveRandomUserTasks(rdbmsService);

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                resourceAccessChecksFromResourceIds(
                    AuthorizationResourceType.PROCESS_DEFINITION, userTask.processDefinitionId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByAuthorizedTenantId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);
    createAndSaveRandomUserTasks(rdbmsService);

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b), resourceAccessChecksFromTenantIds(userTask.tenantId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByPropertyBasedAuthorizationWithAssignee(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);
    createAndSaveRandomUserTasks(rdbmsService);

    // when
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        Authorization.of(b -> b.userTask().read().authorizedByAssignee())),
                    TenantCheck.disabled(),
                    CamundaAuthentication.of(builder -> builder.user(userTask.assignee()))));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByPropertyBasedAuthorizationWithCandidateUser(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);
    createAndSaveRandomUserTasks(rdbmsService);

    // when - authenticate as the first candidate user
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        Authorization.of(b -> b.userTask().read().authorizedByCandidateUsers())),
                    TenantCheck.disabled(),
                    CamundaAuthentication.of(
                        builder -> builder.user(userTask.candidateUsers().getFirst()))));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByPropertyBasedAuthorizationWithCandidateGroup(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);
    createAndSaveRandomUserTasks(rdbmsService);

    // when - authenticate with the first candidate group
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        Authorization.of(b -> b.userTask().read().authorizedByCandidateGroups())),
                    TenantCheck.disabled(),
                    CamundaAuthentication.of(
                        builder ->
                            builder.groupIds(List.of(userTask.candidateGroups().getFirst())))));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindSeveralUserTaskByPropertyBasedAuthorizationWithCandidateGroups(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String group = generateRandomString("my_group");
    final UserTaskDbModel task1 =
        UserTaskFixtures.createRandomized(b -> b.candidateGroups(List.of(group)));
    final UserTaskDbModel task2 =
        UserTaskFixtures.createRandomized(b -> b.candidateGroups(List.of(group)));
    createAndSaveUserTask(rdbmsService, task1);
    createAndSaveUserTask(rdbmsService, task2);
    createAndSaveRandomUserTasks(rdbmsService);

    // when - authenticate with the candidate group that matches both tasks
    final var userGroups = List.of(group, generateRandomString("unrelatedGroup"));
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        Authorization.of(b -> b.userTask().read().authorizedByCandidateGroups())),
                    TenantCheck.disabled(),
                    CamundaAuthentication.of(builder -> builder.groupIds(userGroups))));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items())
        .extracting(UserTaskEntity::userTaskKey)
        .containsExactlyInAnyOrder(task1.userTaskKey(), task2.userTaskKey());
  }

  @TestTemplate
  public void shouldFindUserTaskByPropertyBasedAuthorizationCombiningMultipleProperties(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // Create three tasks - we'll make them all match a single user authentication
    final UserTaskDbModel task1 = UserTaskFixtures.createRandomized();
    final UserTaskDbModel task2 =
        UserTaskFixtures.createRandomized(b -> b.candidateUsers(List.of(task1.assignee())));
    final UserTaskDbModel task3 = UserTaskFixtures.createRandomized();

    createAndSaveUserTask(rdbmsService, task1);
    createAndSaveUserTask(rdbmsService, task2);
    createAndSaveUserTask(rdbmsService, task3);
    createAndSaveRandomUserTasks(rdbmsService);

    // when
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        Authorization.of(
                            b ->
                                b.userTask()
                                    .read()
                                    .authorizedByAssignee()
                                    .or()
                                    .authorizedByCandidateUsers()
                                    .or()
                                    .authorizedByCandidateGroups())),
                    TenantCheck.disabled(),
                    CamundaAuthentication.of(
                        builder ->
                            builder
                                // matches task1 by assignee and task2 by candidate user
                                .user(task1.assignee())
                                // matches task3 by candidate group
                                .groupIds(List.of(task3.candidateGroups().getFirst())))));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(3);
    assertThat(searchResult.items()).hasSize(3);
    assertThat(searchResult.items())
        .extracting(UserTaskEntity::userTaskKey)
        .containsExactlyInAnyOrder(task1.userTaskKey(), task2.userTaskKey(), task3.userTaskKey());
  }

  @TestTemplate
  public void shouldFindUserTaskByCompositeAuthorizationWhenBothBranchesMatch(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);
    createAndSaveRandomUserTasks(rdbmsService);

    // create another user task to ensure only the intended one is matched
    createAndSaveUserTask(rdbmsService, UserTaskFixtures.createRandomized());

    // when
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        // both branches match the user task
                        AuthorizationConditions.anyOf(
                            Authorization.of(
                                b ->
                                    b.processDefinition()
                                        .resourceId(userTask.processDefinitionId())),
                            Authorization.of(
                                b ->
                                    b.userTask()
                                        .read()
                                        .resourceId(String.valueOf(userTask.userTaskKey()))))),
                    TenantCheck.disabled()));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByCompositeAuthorizationWhenOnlyProcessDefinitionMatches(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);
    createAndSaveRandomUserTasks(rdbmsService);

    // create another user task to ensure only the intended one is matched
    createAndSaveUserTask(rdbmsService, UserTaskFixtures.createRandomized());

    final long nonExistentTaskKey = 99993999992L;

    // when
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        AuthorizationConditions.anyOf(
                            // only process definition branch matches
                            Authorization.of(
                                b ->
                                    b.processDefinition()
                                        .resourceId(userTask.processDefinitionId())),
                            Authorization.of(
                                b ->
                                    b.userTask()
                                        .read()
                                        .resourceId(String.valueOf(nonExistentTaskKey))))),
                    TenantCheck.disabled()));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByCompositeAuthorizationWhenOnlyUserTaskMatches(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);
    createAndSaveRandomUserTasks(rdbmsService);

    // create another user task to ensure only the intended one is matched
    createAndSaveUserTask(rdbmsService, UserTaskFixtures.createRandomized());

    // when
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        AuthorizationConditions.anyOf(
                            Authorization.of(
                                b -> b.processDefinition().resourceId("non-existent-process-def")),
                            // only user task branch matches
                            Authorization.of(
                                b ->
                                    b.userTask()
                                        .read()
                                        .resourceId(String.valueOf(userTask.userTaskKey()))))),
                    TenantCheck.disabled()));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldReturnEmptyResultWhenNeitherCompositeAuthorizationBranchMatches(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);
    createAndSaveRandomUserTasks(rdbmsService);

    final long nonExistentTaskKey = 99993999992L;

    // when
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        // either branch matches
                        AuthorizationConditions.anyOf(
                            Authorization.of(
                                b -> b.processDefinition().resourceId("non-existent-process-def")),
                            Authorization.of(
                                b ->
                                    b.userTask()
                                        .read()
                                        .resourceId(String.valueOf(nonExistentTaskKey))))),
                    TenantCheck.disabled()));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(0);
    assertThat(searchResult.items()).isEmpty();
  }

  @TestTemplate
  public void shouldFindMultipleUserTasksByCompositeAuthorizationWithPartialMatches(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // create two user tasks with different process definitions
    final UserTaskDbModel userTask1 =
        UserTaskFixtures.createRandomized(b -> b.processDefinitionId("proc-def-1"));
    final UserTaskDbModel userTask2 =
        UserTaskFixtures.createRandomized(b -> b.processDefinitionId("proc-def-2"));
    createAndSaveUserTask(rdbmsService, userTask1);
    createAndSaveUserTask(rdbmsService, userTask2);
    createAndSaveRandomUserTasks(rdbmsService);

    // create another user task to ensure only the intended ones are matched
    createAndSaveUserTask(rdbmsService, UserTaskFixtures.createRandomized());

    // when
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        // composite authorization: proc-def-1 OR userTask2's key
                        AuthorizationConditions.anyOf(
                            Authorization.of(b -> b.processDefinition().resourceId("proc-def-1")),
                            Authorization.of(
                                b ->
                                    b.userTask()
                                        .read()
                                        .resourceId(String.valueOf(userTask2.userTaskKey()))))),
                    TenantCheck.disabled()));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items())
        .extracting(UserTaskEntity::userTaskKey)
        .containsExactlyInAnyOrder(userTask1.userTaskKey(), userTask2.userTaskKey());
  }

  @TestTemplate
  public void shouldFindUserTaskByCompositeAuthorizationWithMultipleResourceIdsPerType(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask1 =
        UserTaskFixtures.createRandomized(b -> b.processDefinitionId("proc-def-A"));
    final UserTaskDbModel userTask2 =
        UserTaskFixtures.createRandomized(b -> b.processDefinitionId("proc-def-B"));
    final UserTaskDbModel userTask3 = UserTaskFixtures.createRandomized();
    final UserTaskDbModel userTask4 = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask1);
    createAndSaveUserTask(rdbmsService, userTask2);
    createAndSaveUserTask(rdbmsService, userTask3);
    createAndSaveUserTask(rdbmsService, userTask4);
    createAndSaveRandomUserTasks(rdbmsService);

    // create another user task to ensure only the intended ones are matched
    createAndSaveUserTask(rdbmsService, UserTaskFixtures.createRandomized());

    // when
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        // multiple IDs for PROCESS_DEFINITION and USER_TASK
                        AuthorizationConditions.anyOf(
                            Authorization.of(
                                b ->
                                    b.processDefinition()
                                        .resourceIds(List.of("proc-def-A", "proc-def-B"))),
                            Authorization.of(
                                b ->
                                    b.userTask()
                                        .read()
                                        .resourceIds(
                                            List.of(
                                                String.valueOf(userTask3.userTaskKey()),
                                                String.valueOf(userTask4.userTaskKey())))))),
                    TenantCheck.disabled()));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(4);
    assertThat(searchResult.items()).hasSize(4);
    assertThat(searchResult.items())
        .extracting(UserTaskEntity::userTaskKey)
        .containsExactlyInAnyOrder(
            userTask1.userTaskKey(),
            userTask2.userTaskKey(),
            userTask3.userTaskKey(),
            userTask4.userTaskKey());
  }

  @TestTemplate
  public void shouldFindUserTaskByCompositeAuthorizationWhenOnlyIdBranchMatches(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);
    createAndSaveRandomUserTasks(rdbmsService);

    // when - combining ID-based and property-based, but only ID matches
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        AuthorizationConditions.anyOf(
                            // ID-based: matches
                            Authorization.of(
                                b ->
                                    b.userTask()
                                        .read()
                                        .resourceId(String.valueOf(userTask.userTaskKey()))),
                            // Property-based: doesn't match (different user)
                            Authorization.of(b -> b.userTask().read().authorizedByAssignee()))),
                    TenantCheck.disabled(),
                    CamundaAuthentication.of(
                        builder -> builder.user(generateRandomString("different-user")))));

    // then - should find the task because ID branch matches
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByCompositeAuthorizationWhenOnlyPropertyBranchMatches(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);
    createAndSaveRandomUserTasks(rdbmsService);

    final long nonExistentTaskKey = 99993999992L;

    // when - combining ID-based and property-based, but only property matches
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        AuthorizationConditions.anyOf(
                            // ID-based: doesn't match (wrong task key)
                            Authorization.of(
                                b ->
                                    b.userTask()
                                        .read()
                                        .resourceId(String.valueOf(nonExistentTaskKey))),
                            // Property-based: matches by assignee
                            Authorization.of(b -> b.userTask().read().authorizedByAssignee()))),
                    TenantCheck.disabled(),
                    CamundaAuthentication.of(builder -> builder.user(userTask.assignee()))));

    // then - should find the task because property branch matches
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void
      shouldFindMultipleUserTasksByCompositeAuthorizationWithIdAndPropertyBasedPartialMatches(
          final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // Create three tasks with different authorization scenarios
    final UserTaskDbModel task1 = UserTaskFixtures.createRandomized();
    final UserTaskDbModel task2 = UserTaskFixtures.createRandomized();
    final UserTaskDbModel task3 = UserTaskFixtures.createRandomized();

    createAndSaveUserTask(rdbmsService, task1);
    createAndSaveUserTask(rdbmsService, task2);
    createAndSaveUserTask(rdbmsService, task3);
    createAndSaveRandomUserTasks(rdbmsService);

    // when
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskQuery.of(b -> b),
                ResourceAccessChecks.of(
                    AuthorizationCheck.enabled(
                        AuthorizationConditions.anyOf(
                            // ID-based: matches task1
                            Authorization.of(
                                b ->
                                    b.userTask()
                                        .read()
                                        .resourceId(String.valueOf(task1.userTaskKey()))),
                            // Property-based: matches task2 by assignee
                            Authorization.of(b -> b.userTask().read().authorizedByAssignee()),
                            // Property-based: matches task3 by candidate group
                            Authorization.of(
                                b -> b.userTask().read().authorizedByCandidateGroups()))),
                    TenantCheck.disabled(),
                    CamundaAuthentication.of(
                        builder ->
                            builder
                                .user(task2.assignee())
                                .groupIds(List.of(task3.candidateGroups().getFirst())))));

    // then - should find task1 (ID), task2 (assignee), task3 (candidate group)
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(3);
    assertThat(searchResult.items()).hasSize(3);
    assertThat(searchResult.items())
        .extracting(UserTaskEntity::userTaskKey)
        .containsExactlyInAnyOrder(task1.userTaskKey(), task2.userTaskKey(), task3.userTaskKey());
  }

  @TestTemplate
  public void shouldFindUserTaskByProcessInstanceVariableName(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);

    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(
            b ->
                b.processInstanceKey(userTask.processInstanceKey())
                    .name("randomVariable" + UUID.randomUUID()));
    final RdbmsWriters writer = rdbmsService.createWriter(1L);
    writer.getVariableWriter().create(randomizedVariable);
    writer.flush();

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .processInstanceVariables(
                            List.of(
                                new VariableValueFilter.Builder()
                                    .name(randomizedVariable.name())
                                    .build()))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByTwoProcessInstanceVariableNames(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);

    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(
            b ->
                b.processInstanceKey(userTask.processInstanceKey())
                    .name("randomVariable" + UUID.randomUUID()));
    final VariableDbModel randomizedVariable2 =
        VariableFixtures.createRandomized(
            b ->
                b.processInstanceKey(userTask.processInstanceKey())
                    .name("randomVariable" + UUID.randomUUID()));
    final RdbmsWriters writer = rdbmsService.createWriter(1L);
    writer.getVariableWriter().create(randomizedVariable);
    writer.getVariableWriter().create(randomizedVariable2);
    writer.flush();

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .processInstanceVariables(
                            List.of(
                                new VariableValueFilter.Builder()
                                    .name(randomizedVariable.name())
                                    .build(),
                                new VariableValueFilter.Builder()
                                    .name(randomizedVariable2.name())
                                    .build()))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByProcessInstanceVariableNameAndValue(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);

    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.processInstanceKey(userTask.processInstanceKey()));
    final RdbmsWriters writer = rdbmsService.createWriter(1L);
    writer.getVariableWriter().create(randomizedVariable);
    writer.flush();

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .processInstanceVariables(
                            List.of(
                                new VariableValueFilter.Builder()
                                    .name(randomizedVariable.name())
                                    .valueOperation(
                                        UntypedOperation.of(
                                            Operation.eq(randomizedVariable.value())))
                                    .build()))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByTwoProcessInstanceVariableNamesAndValues(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);

    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(
            b ->
                b.processInstanceKey(userTask.processInstanceKey())
                    .name("randomVariable" + UUID.randomUUID()));
    final VariableDbModel randomizedVariable2 =
        VariableFixtures.createRandomized(
            b ->
                b.processInstanceKey(userTask.processInstanceKey())
                    .name("randomVariable" + UUID.randomUUID()));

    final RdbmsWriters writer = rdbmsService.createWriter(1L);
    writer.getVariableWriter().create(randomizedVariable);
    writer.getVariableWriter().create(randomizedVariable2);
    writer.flush();

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .processInstanceVariables(
                            List.of(
                                new VariableValueFilter.Builder()
                                    .name(randomizedVariable.name())
                                    .valueOperation(
                                        UntypedOperation.of(
                                            Operation.eq(randomizedVariable.value())))
                                    .build(),
                                new VariableValueFilter.Builder()
                                    .name(randomizedVariable2.name())
                                    .valueOperation(
                                        UntypedOperation.of(
                                            Operation.eq(randomizedVariable2.value())))
                                    .build()))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByLocalVariableName(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);

    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.scopeKey(userTask.elementInstanceKey()));
    final RdbmsWriters writer = rdbmsService.createWriter(1L);
    writer.getVariableWriter().create(randomizedVariable);
    writer.flush();

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .localVariables(
                            List.of(
                                new VariableValueFilter.Builder()
                                    .name(randomizedVariable.name())
                                    .build()))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByTwoLocalVariableNames(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);

    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(
            b ->
                b.scopeKey(userTask.elementInstanceKey())
                    .name("localVariable" + UUID.randomUUID()));

    final VariableDbModel randomizedVariable2 =
        VariableFixtures.createRandomized(
            b ->
                b.scopeKey(userTask.elementInstanceKey())
                    .name("localVariable" + UUID.randomUUID()));

    final RdbmsWriters writer = rdbmsService.createWriter(1L);
    writer.getVariableWriter().create(randomizedVariable);
    writer.getVariableWriter().create(randomizedVariable2);
    writer.flush();

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .localVariables(
                            List.of(
                                new VariableValueFilter.Builder()
                                    .name(randomizedVariable.name())
                                    .build(),
                                new VariableValueFilter.Builder()
                                    .name(randomizedVariable2.name())
                                    .build()))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindAllUserTasksPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String processDefinitionId = UserTaskFixtures.nextStringId();
    createAndSaveRandomUserTasks(rdbmsService, processDefinitionId);

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder().bpmnProcessIds(processDefinitionId).build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllUserTasksPageValuesAreNull(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String processDefinitionId = UserTaskFixtures.nextStringId();
    createAndSaveRandomUserTasks(rdbmsService, processDefinitionId);

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder().bpmnProcessIds(processDefinitionId).build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(20);
  }

  @TestTemplate
  public void shouldFindUserTaskByCreationDateGt(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var creationDate = NOW.plusDays(15).truncatedTo(ChronoUnit.SECONDS);

    final UserTaskDbModel userTask =
        UserTaskFixtures.createRandomized(b -> b.creationDate(creationDate));
    createAndSaveUserTask(rdbmsService, userTask);

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .creationDateOperations(Operation.gt(creationDate.minusDays(1)))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByCompletionDateGte(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var completionDate = NOW.plusDays(15).truncatedTo(ChronoUnit.SECONDS);

    final UserTaskDbModel userTask =
        UserTaskFixtures.createRandomized(b -> b.completionDate(completionDate));
    createAndSaveUserTask(rdbmsService, userTask);

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .completionDateOperations(Operation.gte(completionDate))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByCreationDateLte(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var creationDate = NOW.minusDays(20).truncatedTo(ChronoUnit.SECONDS);

    final UserTaskDbModel userTask =
        UserTaskFixtures.createRandomized(b -> b.creationDate(creationDate));
    createAndSaveUserTask(rdbmsService, userTask);

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .creationDateOperations(Operation.lte(creationDate))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByCompletionDateLt(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var completionDate = NOW.minusDays(15).truncatedTo(ChronoUnit.SECONDS);

    final UserTaskDbModel userTask =
        UserTaskFixtures.createRandomized(b -> b.completionDate(completionDate));
    createAndSaveUserTask(rdbmsService, userTask);

    final var searchResultGte =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .completionDateOperations(Operation.lt(completionDate.plusDays(1)))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResultGte.total()).isEqualTo(1);
    assertThat(searchResultGte.items()).hasSize(1);
    assertUserTaskEntity(searchResultGte.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByDueDateGt(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var dueDate = NOW.plusDays(15).truncatedTo(ChronoUnit.SECONDS);

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized(b -> b.dueDate(dueDate));
    createAndSaveUserTask(rdbmsService, userTask);

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .dueDateOperations(Operation.gt(dueDate.minusDays(1)))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByDueDateLte(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var dueDate = NOW.minusDays(20).truncatedTo(ChronoUnit.SECONDS);

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized(b -> b.dueDate(dueDate));
    createAndSaveUserTask(rdbmsService, userTask);

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder().dueDateOperations(Operation.lte(dueDate)).build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByFollowUpDateGt(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var followUpDate = NOW.plusDays(10).truncatedTo(ChronoUnit.SECONDS);

    final UserTaskDbModel userTask =
        UserTaskFixtures.createRandomized(b -> b.followUpDate(followUpDate));
    createAndSaveUserTask(rdbmsService, userTask);

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .followUpDateOperations(Operation.gt(followUpDate.minusDays(1)))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByFollowUpDateLte(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var followUpDate = NOW.minusDays(12).truncatedTo(ChronoUnit.SECONDS);

    final UserTaskDbModel userTask =
        UserTaskFixtures.createRandomized(b -> b.followUpDate(followUpDate));
    createAndSaveUserTask(rdbmsService, userTask);

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .followUpDateOperations(Operation.lte(followUpDate))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertUserTaskEntity(searchResult.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskByCompletionDateEquals(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var completionDate = NOW.minusDays(16).truncatedTo(ChronoUnit.SECONDS);

    final UserTaskDbModel userTask =
        UserTaskFixtures.createRandomized(b -> b.completionDate(completionDate));
    createAndSaveUserTask(rdbmsService, userTask);

    final var searchResultGte =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder()
                        .completionDateOperations(Operation.eq(completionDate))
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResultGte.total()).isEqualTo(1);
    assertThat(searchResultGte.items()).hasSize(1);
    assertUserTaskEntity(searchResultGte.items().getFirst(), userTask);
  }

  @TestTemplate
  public void shouldFindUserTaskWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final UserTaskDbReader processInstanceReader = rdbmsService.getUserTaskReader();

    createAndSaveRandomUserTasks(rdbmsService);
    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized(b -> b);
    createAndSaveUserTask(rdbmsService, userTask);

    final var searchResult =
        processInstanceReader.search(
            new UserTaskQuery(
                new UserTaskFilter.Builder()
                    .userTaskKeys(userTask.userTaskKey())
                    .elementIds(userTask.elementId())
                    .assignees(userTask.assignee())
                    .states(userTask.state().name())
                    .processInstanceKeys(userTask.processInstanceKey())
                    .processDefinitionKeys(userTask.processDefinitionKey())
                    .candidateUserOperations(Operation.in(userTask.candidateUsers()))
                    .candidateGroupOperations(Operation.in(userTask.candidateGroups()))
                    .creationDateOperations(Operation.lt(userTask.creationDate().plusDays(1)))
                    .completionDateOperations(Operation.gte(userTask.completionDate().minusDays(2)))
                    .dueDateOperations(Operation.lte(userTask.dueDate().plusDays(1)))
                    .followUpDateOperations(Operation.gte(userTask.followUpDate().minusDays(1)))
                    .tenantIds(userTask.tenantId())
                    .build(),
                UserTaskSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().userTaskKey()).isEqualTo(userTask.userTaskKey());
  }

  @TestTemplate
  public void shouldFindWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final UserTaskDbReader reader = rdbmsService.getUserTaskReader();

    createAndSaveRandomUserTasks(rdbmsService, b -> b.tenantId("tenant-1337"));
    final var sort = UserTaskSort.of(s -> s.priority().asc().completionDate().asc().desc());
    final var searchResult =
        reader.search(
            UserTaskQuery.of(
                b ->
                    b.filter(f -> f.tenantIds("tenant-1337"))
                        .sort(sort)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.items())
        .isSortedAccordingTo(Comparator.comparing(UserTaskEntity::priority));

    final var firstPage =
        reader.search(
            UserTaskQuery.of(
                b -> b.filter(f -> f.tenantIds("tenant-1337")).sort(sort).page(p -> p.size(15))));

    final var nextPage =
        reader.search(
            UserTaskQuery.of(
                b ->
                    b.filter(f -> f.tenantIds("tenant-1337"))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);

    // compare only keys to avoid flaky test due random differences in candidate
    // users/groups
    assertThat(nextPage.items())
        .extracting(ut -> ut.userTaskKey())
        .isEqualTo(
            searchResult.items().subList(15, 20).stream()
                .map(UserTaskEntity::userTaskKey)
                .toList());
  }

  private static void assertUserTaskEntity(
      final UserTaskEntity instance, final UserTaskDbModel userTask) {
    assertThat(instance).isNotNull();
    assertThat(instance)
        .usingRecursiveComparison()
        .ignoringFields(
            "customHeaders",
            "creationDate",
            "completionDate",
            "dueDate",
            "followUpDate",
            "candidateUsers",
            "processName",
            "candidateGroups",
            "tags")
        .isEqualTo(userTask);
    assertThat(instance.creationDate())
        .isCloseTo(userTask.creationDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.completionDate())
        .isCloseTo(userTask.completionDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.dueDate())
        .isCloseTo(userTask.dueDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.followUpDate())
        .isCloseTo(userTask.followUpDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));

    if (userTask.candidateUsers() != null) {
      assertThat(instance.candidateUsers())
          .containsExactlyInAnyOrderElementsOf(userTask.candidateUsers());
    } else {
      assertThat(instance.candidateUsers()).isEmpty();
    }

    if (userTask.candidateGroups() != null) {
      assertThat(instance.candidateGroups())
          .containsExactlyInAnyOrderElementsOf(userTask.candidateGroups());
    } else {
      assertThat(instance.candidateGroups()).isEmpty();
    }

    if (userTask.tags() != null) {
      assertThat(instance.tags()).containsExactlyInAnyOrderElementsOf(userTask.tags());
    } else {
      assertThat(instance.tags()).isEmpty();
    }
  }

  @TestTemplate
  public void shouldDeleteRootProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final UserTaskDbReader reader = rdbmsService.getUserTaskReader();

    final var definition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    final var item1 =
        createAndSaveUserTask(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item2 =
        createAndSaveUserTask(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item3 =
        createAndSaveUserTask(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));

    // when
    final int deleted =
        rdbmsWriters
            .getUserTaskWriter()
            .deleteRootProcessInstanceRelatedData(List.of(item2.rootProcessInstanceKey()), 10);

    // then
    assertThat(deleted).isEqualTo(1);
    final var searchResult =
        reader.search(
            UserTaskQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(definition.processDefinitionKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(UserTaskEntity::userTaskKey))
        .containsExactlyInAnyOrder(item1.userTaskKey(), item3.userTaskKey());
  }
}
