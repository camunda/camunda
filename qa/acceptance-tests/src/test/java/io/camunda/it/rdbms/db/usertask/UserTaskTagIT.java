/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.usertask;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.UserTaskFixtures.createAndSaveUserTask;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.read.service.UserTaskDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.fixtures.UserTaskFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.query.UserTaskQuery;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class UserTaskTagIT {

  private static final int PARTITION_ID = 0;
  private static final String TAG_FOO = "foo";
  private static final String TAG_BAR = "bar";

  @TestTemplate
  public void shouldFindUserTaskWithSingleTag(final CamundaRdbmsTestApplication testApplication) {
    final var reader = setupReader(testApplication);
    final var writer = setupWriter(testApplication);

    final var processInstanceKey = nextKey();
    createAndSaveUserTask(
        writer,
        UserTaskFixtures.createRandomized(
            b -> b.processInstanceKey(processInstanceKey).tags(Set.of(TAG_FOO))));

    final var searchResult =
        reader.search(
            UserTaskQuery.of(
                b ->
                    b.filter(
                        f -> f.processInstanceKeys(processInstanceKey).tags(Set.of(TAG_FOO)))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items().getFirst().tags()).containsOnly(TAG_FOO);
  }

  @TestTemplate
  public void shouldFindOnlyUserTasksWithAllRequestedTags(
      final CamundaRdbmsTestApplication testApplication) {
    final var reader = setupReader(testApplication);
    final var writer = setupWriter(testApplication);

    final var processInstanceKey = nextKey();

    // Create user tasks with different tag combinations
    createAndSaveUserTask(
        writer,
        UserTaskFixtures.createRandomized(
            b -> b.processInstanceKey(processInstanceKey).tags(Set.of(TAG_FOO))));

    createAndSaveUserTask(
        writer,
        UserTaskFixtures.createRandomized(
            b -> b.processInstanceKey(processInstanceKey).tags(Set.of(TAG_BAR))));

    createAndSaveUserTask(
        writer,
        UserTaskFixtures.createRandomized(
            b -> b.processInstanceKey(processInstanceKey).tags(Set.of(TAG_FOO, TAG_BAR))));

    // Search for both tags - should only return the task with BOTH tags (AND logic)
    final var searchResult =
        reader.search(
            UserTaskQuery.of(
                b ->
                    b.filter(
                        f ->
                            f.processInstanceKeys(processInstanceKey)
                                .tags(Set.of(TAG_FOO, TAG_BAR)))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items().getFirst().tags()).containsExactlyInAnyOrder(TAG_FOO, TAG_BAR);
  }

  @TestTemplate
  public void shouldNotFindUserTasksWithoutTags(final CamundaRdbmsTestApplication testApplication) {
    final var reader = setupReader(testApplication);
    final var writer = setupWriter(testApplication);

    final var processInstanceKey = nextKey();

    // Create user task without tags and one with tags
    createAndSaveUserTask(
        writer,
        UserTaskFixtures.createRandomized(
            b -> b.processInstanceKey(processInstanceKey).tags(Set.of())));

    createAndSaveUserTask(
        writer,
        UserTaskFixtures.createRandomized(
            b -> b.processInstanceKey(processInstanceKey).tags(Set.of(TAG_FOO))));

    // Search for tag should only return the task with tags
    final var searchResult =
        reader.search(
            UserTaskQuery.of(
                b ->
                    b.filter(
                        f -> f.processInstanceKeys(processInstanceKey).tags(Set.of(TAG_FOO)))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items().getFirst().tags()).containsOnly(TAG_FOO);
  }

  private static UserTaskDbReader setupReader(final CamundaRdbmsTestApplication testApplication) {
    return testApplication.getRdbmsService().getUserTaskReader();
  }

  private static RdbmsWriters setupWriter(final CamundaRdbmsTestApplication testApplication) {
    return testApplication.getRdbmsService().createWriter(PARTITION_ID);
  }
}
