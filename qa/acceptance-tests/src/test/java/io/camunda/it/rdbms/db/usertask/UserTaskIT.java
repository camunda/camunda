/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.usertask;

import static io.camunda.it.rdbms.db.fixtures.UserTaskFixtures.createAndSaveRandomUserTasks;
import static io.camunda.it.rdbms.db.fixtures.UserTaskFixtures.createAndSaveUserTask;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.UserTaskReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
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
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
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
    final RdbmsWriter writer = rdbmsService.createWriter(1L);
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
    final RdbmsWriter writer = rdbmsService.createWriter(1L);
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
    final RdbmsWriter writer = rdbmsService.createWriter(1L);
    writer.getUserTaskWriter().update(updatedModel);
    writer.flush();

    final var instance = rdbmsService.getUserTaskReader().findOne(userTask.userTaskKey()).get();
    assertUserTaskEntity(instance, updatedModel);
  }

  @TestTemplate
  public void shouldUpdateCandidateUserAndGroupAndFindByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(1L);

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsWriter, userTask);

    final UserTaskDbModel updatedModel =
        userTask.toBuilder()
            .candidateUsers(List.of("user1", "user2", "user3"))
            .candidateGroups(List.of("group1", "group2"))
            .build();
    rdbmsWriter.getUserTaskWriter().update(updatedModel);
    rdbmsWriter.flush();

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
  public void shouldFindUserTaskByProcessInstanceVariableName(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);

    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.processInstanceKey(userTask.processInstanceKey()));
    final RdbmsWriter writer = rdbmsService.createWriter(1L);
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
  public void shouldFindUserTaskByProcessInstanceVariableNameAndValue(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);

    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.processInstanceKey(userTask.processInstanceKey()));
    final RdbmsWriter writer = rdbmsService.createWriter(1L);
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
  public void shouldFindUserTaskByLocalVariableName(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final UserTaskDbModel userTask = UserTaskFixtures.createRandomized();
    createAndSaveUserTask(rdbmsService, userTask);

    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(
            b -> b.scopeKey(userTask.elementInstanceKey()).name("localVariable"));
    final RdbmsWriter writer = rdbmsService.createWriter(1L);
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
                                new VariableValueFilter.Builder().name("localVariable").build()))
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
    final UserTaskReader processInstanceReader = rdbmsService.getUserTaskReader();

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
    final UserTaskReader reader = rdbmsService.getUserTaskReader();

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
    final var instanceAfter = searchResult.items().get(9);
    final var nextPage =
        reader.search(
            UserTaskQuery.of(
                b ->
                    b.filter(f -> f.tenantIds("tenant-1337"))
                        .sort(sort)
                        .page(
                            p ->
                                p.size(5)
                                    .searchAfter(
                                        new Object[] {
                                          instanceAfter.priority(),
                                          instanceAfter.completionDate(),
                                          instanceAfter.userTaskKey()
                                        }))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(10, 15));
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
            "candidateGroups")
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
  }

  @TestTemplate
  public void shouldCleanup(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final UserTaskReader reader = rdbmsService.getUserTaskReader();

    final var cleanupDate = NOW.minusDays(1);

    final var definition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriter, b -> b);
    final var item1 =
        createAndSaveUserTask(
            rdbmsWriter, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item2 =
        createAndSaveUserTask(
            rdbmsWriter, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item3 =
        createAndSaveUserTask(
            rdbmsWriter, b -> b.processDefinitionKey(definition.processDefinitionKey()));

    // set cleanup dates
    rdbmsWriter.getUserTaskWriter().scheduleForHistoryCleanup(item1.processInstanceKey(), NOW);
    rdbmsWriter
        .getUserTaskWriter()
        .scheduleForHistoryCleanup(item2.processInstanceKey(), NOW.minusDays(2));
    rdbmsWriter.flush();

    // cleanup
    rdbmsWriter.getUserTaskWriter().cleanupHistory(PARTITION_ID, cleanupDate, 10);

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
