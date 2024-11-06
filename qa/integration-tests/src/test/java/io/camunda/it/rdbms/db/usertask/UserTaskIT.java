/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.usertask;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.NOW;
import static io.camunda.it.rdbms.db.fixtures.UserTaskFixtures.createAndSaveRandomUserTasks;
import static io.camunda.it.rdbms.db.fixtures.UserTaskFixtures.createAndSaveUserTask;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.domain.UserTaskDbQuery;
import io.camunda.db.rdbms.read.service.UserTaskReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.UserTaskState;
import io.camunda.it.rdbms.db.fixtures.UserTaskFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.UserTaskSort;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class UserTaskIT {

  @TestTemplate
  public void shouldCreateAndFindUserTaskByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel randomizedUserTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, randomizedUserTask);

    final var instance = rdbmsService.getUserTaskReader().findOne(randomizedUserTask.key());
    assertUserTaskEntity(instance, randomizedUserTask);
  }

  @TestTemplate
  public void shouldUpdateAndFindUserTaskByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel randomizedUserTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, randomizedUserTask);

    final UserTaskDbModel updatedModel =
        randomizedUserTask.toBuilder()
            .state(UserTaskState.COMPLETED)
            .completionTime(NOW.plusDays(2))
            .build();
    rdbmsService.createWriter(1L).getUserTaskWriter().update(updatedModel);

    final var instance = rdbmsService.getUserTaskReader().findOne(randomizedUserTask.key());
    assertUserTaskEntity(instance, randomizedUserTask);
  }

  @TestTemplate
  public void shouldUpdateCandidateUserAndGroupAndFindByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(1L);

    final UserTaskDbModel randomizedUserTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsWriter, randomizedUserTask);

    final UserTaskDbModel updatedModel =
        randomizedUserTask.toBuilder()
            .candidateUsers(List.of("user1", "user2", "user3"))
            .candidateGroups(List.of("group1", "group2"))
            .build();
    rdbmsWriter.getUserTaskWriter().update(updatedModel);
    rdbmsWriter.flush();

    final var instance = rdbmsService.getUserTaskReader().findOne(randomizedUserTask.key());
    assertUserTaskEntity(instance, updatedModel);
  }

  @TestTemplate
  public void shouldFindUserTaskByProcessInstanceKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel randomizedUserTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, randomizedUserTask);

    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                new UserTaskDbQuery(
                    new UserTaskFilter.Builder()
                        .processInstanceKeys(randomizedUserTask.processInstanceKey())
                        .build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertUserTaskEntity(searchResult.hits().getFirst(), randomizedUserTask);
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
                new UserTaskDbQuery(
                    new UserTaskFilter.Builder().bpmnProcessIds(processDefinitionId).build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(5);
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
                new UserTaskDbQuery(
                    new UserTaskFilter.Builder().bpmnProcessIds(processDefinitionId).build(),
                    UserTaskSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(20);
  }

  @TestTemplate
  public void shouldFindUserTaskWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final UserTaskReader processInstanceReader = rdbmsService.getUserTaskReader();

    final String processDefinitionId = UserTaskFixtures.nextStringId();
    createAndSaveRandomUserTasks(rdbmsService, processDefinitionId);
    final UserTaskDbModel randomizedUserTask =
        UserTaskFixtures.createRandomized(b -> b.processDefinitionId(processDefinitionId));
    createAndSaveUserTask(rdbmsService, randomizedUserTask);

    final var searchResult =
        processInstanceReader.search(
            new UserTaskDbQuery(
                new UserTaskFilter.Builder()
                    .userTaskKeys(randomizedUserTask.key())
                    .elementIds(randomizedUserTask.flowNodeBpmnId())
                    .assignees(randomizedUserTask.assignee())
                    .states(randomizedUserTask.state().name())
                    .processInstanceKeys(randomizedUserTask.processInstanceKey())
                    .processDefinitionKeys(randomizedUserTask.processDefinitionKey())
                    .candidateUserOperations(Operation.in(randomizedUserTask.candidateUsers()))
                    .candidateGroupOperations(Operation.in(randomizedUserTask.candidateGroups()))
                    .tenantIds(randomizedUserTask.tenantId())
                    .build(),
                UserTaskSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(randomizedUserTask.key());
  }

  private static void assertUserTaskEntity(
      final UserTaskEntity instance, final UserTaskDbModel randomizedUserTask) {
    assertThat(instance).isNotNull();
    assertThat(instance)
        .usingRecursiveComparison()
        .ignoringFields(
            "processInstanceId",
            "bpmnProcessId",
            "customHeaders",
            "flowNodeInstanceId",
            "processDefinitionId",
            "creationTime",
            "completionTime",
            "dueDate",
            "followUpDate",
            "candidateUsers",
            "candidateGroups")
        .isEqualTo(randomizedUserTask);
    assertThat(instance.processInstanceId()).isEqualTo(randomizedUserTask.processInstanceKey());
    assertThat(instance.bpmnProcessId()).isEqualTo(randomizedUserTask.processDefinitionId());
    assertThat(instance.flowNodeInstanceId()).isEqualTo(randomizedUserTask.elementInstanceKey());
    assertThat(instance.processDefinitionId()).isEqualTo(randomizedUserTask.processDefinitionKey());
    assertThat(instance.creationTime())
        .isCloseTo(
            randomizedUserTask.creationTime(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.completionTime())
        .isCloseTo(
            randomizedUserTask.completionTime(),
            new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.dueDate())
        .isCloseTo(
            randomizedUserTask.dueDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.followUpDate())
        .isCloseTo(
            randomizedUserTask.followUpDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.candidateUsers())
        .containsExactlyInAnyOrderElementsOf(randomizedUserTask.candidateUsers());
    assertThat(instance.candidateGroups())
        .containsExactlyInAnyOrderElementsOf(randomizedUserTask.candidateGroups());
  }
}
