/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.usertask;

import static io.camunda.it.rdbms.db.fixtures.UserTaskFixtures.createAndSaveRandomUserTasks;
import static io.camunda.it.rdbms.db.fixtures.UserTaskFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.UserTaskReader;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.sort.UserTaskSort;
import io.camunda.search.sort.UserTaskSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class UserTaskSortIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSortByCreationTimeAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.creationDate().asc(),
        Comparator.comparing(UserTaskEntity::creationDate));
  }

  @TestTemplate
  public void shouldSortByCreationTimeDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.creationDate().desc(),
        Comparator.comparing(UserTaskEntity::creationDate).reversed());
  }

  @TestTemplate
  public void shouldSortByCompletionTimeAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.completionDate().asc(),
        Comparator.comparing(UserTaskEntity::creationDate));
  }

  @TestTemplate
  public void shouldSortByCompletionTimeDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.completionDate().desc(),
        Comparator.comparing(UserTaskEntity::creationDate).reversed());
  }

  @TestTemplate
  public void shouldSortByPriorityAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.priority().asc(),
        Comparator.comparing(UserTaskEntity::priority));
  }

  @TestTemplate
  public void shouldSortByPriorityDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.priority().desc(),
        Comparator.comparing(UserTaskEntity::priority).reversed());
  }

  @TestTemplate
  public void shouldSortByDueDateAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.dueDate().asc(),
        Comparator.comparing(UserTaskEntity::dueDate));
  }

  @TestTemplate
  public void shouldSortByDueDateDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.dueDate().desc(),
        Comparator.comparing(UserTaskEntity::dueDate).reversed());
  }

  @TestTemplate
  public void shouldSortByFollowUpDateAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.followUpDate().asc(),
        Comparator.comparing(UserTaskEntity::followUpDate));
  }

  @TestTemplate
  public void shouldSortByFollowUpDateDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.followUpDate().desc(),
        Comparator.comparing(UserTaskEntity::followUpDate).reversed());
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<UserTaskSort>> sortBuilder,
      final Comparator<UserTaskEntity> comparator) {
    final UserTaskReader reader = rdbmsService.getUserTaskReader();

    final var processDefinitionId = nextStringId();
    createAndSaveRandomUserTasks(rdbmsService, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        reader
            .search(
                new UserTaskQuery(
                    new UserTaskFilter.Builder().bpmnProcessIds(processDefinitionId).build(),
                    UserTaskSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
