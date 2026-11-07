/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.usertask;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.it.rdbms.db.fixtures.UserTaskFixtures.createAndSaveUserTask;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.RdbmsServiceFactory;
import io.camunda.db.rdbms.read.service.UserTaskDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.UserTaskState;
import io.camunda.it.rdbms.db.util.RdbmsDataJdbcTest;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.UserTaskFilter.Builder;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.sort.UserTaskSort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@RdbmsDataJdbcTest
@TestPropertySource(
    properties = {"spring.liquibase.enabled=false", "camunda.data.secondary-storage.type=rdbms"})
public class UserTaskSpecificFilterIT {

  @Autowired private RdbmsServiceFactory rdbmsServiceFactory;
  private RdbmsService rdbmsService;

  private UserTaskDbReader userTaskReader;

  private RdbmsWriters rdbmsWriters;

  @BeforeEach
  public void beforeAll() {
    rdbmsService =
        rdbmsServiceFactory.createRdbmsService(
            DEFAULT_PHYSICAL_TENANT_ID, new SimpleMeterRegistry());
    rdbmsWriters = rdbmsService.createWriter(0L);
    userTaskReader = rdbmsService.getUserTaskReader();
  }

  @ParameterizedTest
  @MethodSource("shouldFindUserTaskWithSpecificFilterParameters")
  public void shouldFindUserTaskWithSpecificFilter(
      final UserTaskFilter filter, final List<Long> expectedKeys) {
    createAndSaveUserTask(
        rdbmsWriters, b -> b.userTaskKey(42L).assignee("user1").state(UserTaskState.CREATED));
    createAndSaveUserTask(
        rdbmsWriters, b -> b.userTaskKey(43L).assignee("user2").state(UserTaskState.CREATED));
    createAndSaveUserTask(
        rdbmsWriters, b -> b.userTaskKey(44L).assignee("user3").state(UserTaskState.COMPLETED));

    final var searchResult =
        userTaskReader.search(
            new UserTaskQuery(
                filter, UserTaskSort.of(b -> b), SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(expectedKeys.size());
    assertThat(searchResult.items().stream().map(UserTaskEntity::userTaskKey).toList())
        .containsExactlyInAnyOrderElementsOf(expectedKeys);
  }

  static Stream<Arguments> shouldFindUserTaskWithSpecificFilterParameters() {
    return Stream.of(
        Arguments.of(
            new UserTaskFilter.Builder()
                .orFilters(List.of(new Builder().assignees("user1").build()))
                .build(),
            List.of(42L)),
        Arguments.of(
            new UserTaskFilter.Builder()
                .orFilters(
                    List.of(
                        new Builder().assignees("user1").build(),
                        new Builder().assignees("user2").build()))
                .build(),
            List.of(42L, 43L)),
        Arguments.of(
            new UserTaskFilter.Builder()
                .states(UserTaskState.COMPLETED.name())
                .orFilters(
                    List.of(
                        new Builder().assignees("user1").build(),
                        new Builder().assignees("user3").build()))
                .build(),
            List.of(44L)),
        Arguments.of(
            new UserTaskFilter.Builder()
                .orFilters(List.of(new Builder().build(), new Builder().assignees("user2").build()))
                .build(),
            List.of(42L, 43L, 44L)));
  }
}
