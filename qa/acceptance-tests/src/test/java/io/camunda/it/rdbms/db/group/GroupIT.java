/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.group;

import static io.camunda.it.rdbms.db.fixtures.GroupFixtures.createAndSaveGroup;
import static io.camunda.it.rdbms.db.fixtures.GroupFixtures.createAndSaveRandomGroups;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.GroupReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.it.rdbms.db.fixtures.GroupFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.sort.GroupSort;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class GroupIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final GroupReader groupReader = rdbmsService.getGroupReader();

    final var group = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriter, group);

    final var instance = groupReader.findOne(group.groupId()).orElse(null);

    compareGroups(instance, group);
  }

  @TestTemplate
  public void shouldSaveAndUpdate(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final GroupReader groupReader = rdbmsService.getGroupReader();

    final var group = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriter, group);

    final var groupUpdate =
        GroupFixtures.createRandomized(b -> b.groupId(group.groupId()).groupKey(group.groupKey()));
    rdbmsWriter.getGroupWriter().update(groupUpdate);
    rdbmsWriter.flush();

    final var instance = groupReader.findOne(group.groupId()).orElse(null);

    compareGroups(instance, groupUpdate);
  }

  @TestTemplate
  public void shouldSaveAndDelete(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final GroupReader groupReader = rdbmsService.getGroupReader();

    final var group = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriter, group);
    final var instance = groupReader.findOne(group.groupId()).orElse(null);
    compareGroups(instance, group);

    rdbmsWriter.getGroupWriter().delete(group.groupId());
    rdbmsWriter.flush();

    final var deletedInstance = groupReader.findOne(group.groupId()).orElse(null);
    assertThat(deletedInstance).isNull();
  }

  @TestTemplate
  public void shouldFindByName(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final GroupReader groupReader = rdbmsService.getGroupReader();

    final var group = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriter, group);

    final var searchResult =
        groupReader.search(
            new GroupQuery(
                new GroupFilter.Builder().name(group.name()).build(),
                GroupSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(instance).isNotNull();
    assertThat(instance.groupKey()).isEqualTo(group.groupKey());
    assertThat(instance.name()).isEqualTo(group.name());
  }

  @TestTemplate
  public void shouldFindAllPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final GroupReader groupReader = rdbmsService.getGroupReader();

    createAndSaveRandomGroups(rdbmsWriter, b -> b.name("John Doe"));

    final var searchResult =
        groupReader.search(
            new GroupQuery(
                new GroupFilter.Builder().name("John Doe").build(),
                GroupSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final GroupReader groupReader = rdbmsService.getGroupReader();

    final var group = GroupFixtures.createRandomized(b -> b);
    createAndSaveRandomGroups(rdbmsWriter);
    createAndSaveGroup(rdbmsWriter, group);

    final var searchResult =
        groupReader.search(
            new GroupQuery(
                new GroupFilter.Builder().groupKey(group.groupKey()).name(group.name()).build(),
                GroupSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().groupKey()).isEqualTo(group.groupKey());
  }

  @Disabled("I think this test is not valid anymore as now the key of the table is a String")
  @TestTemplate
  public void shouldFindWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final GroupReader groupReader = rdbmsService.getGroupReader();

    createAndSaveRandomGroups(rdbmsWriter, b -> b.name("Alice Doe"));
    final var sort = GroupSort.of(s -> s.name().asc());
    final var searchResult =
        groupReader.search(
            GroupQuery.of(
                b -> b.filter(f -> f.name("Alice Doe")).sort(sort).page(p -> p.from(0).size(20))));

    final var instanceAfter = searchResult.items().get(9);
    final var nextPage =
        groupReader.search(
            GroupQuery.of(
                b ->
                    b.filter(f -> f.name("Alice Doe"))
                        .sort(sort)
                        .page(
                            p ->
                                p.size(5)
                                    .searchAfter(
                                        new Object[] {
                                          instanceAfter.name(), instanceAfter.groupKey()
                                        }))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(10, 15));
  }

  private static void compareGroups(final GroupEntity instance, final GroupDbModel group) {
    assertThat(instance).isNotNull();
    assertThat(instance.groupKey()).isEqualTo(group.groupKey());
    assertThat(instance.groupId()).isEqualTo(group.groupId());
    assertThat(instance.name()).isEqualTo(group.name());
    assertThat(instance.description()).isEqualTo(group.description());
  }
}
