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
import static io.camunda.it.rdbms.db.fixtures.GroupFixtures.createAndSaveRandomGroupsWithMembers;
import static io.camunda.it.rdbms.db.fixtures.UserFixtures.createAndSaveUser;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.GroupDbReader;
import io.camunda.db.rdbms.read.service.UserDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import io.camunda.it.rdbms.db.fixtures.GroupFixtures;
import io.camunda.it.rdbms.db.fixtures.UserFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.sort.GroupSort;
import io.camunda.search.sort.UserSort;
import java.time.OffsetDateTime;
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
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final GroupDbReader groupReader = rdbmsService.getGroupReader();

    final var group = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriters, group);

    final var instance = groupReader.findOne(group.groupId()).orElse(null);

    compareGroups(instance, group);
  }

  @TestTemplate
  public void shouldSaveAndFindById(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final GroupDbReader groupReader = rdbmsService.getGroupReader();

    final var group = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriters, group);

    final var instance = groupReader.findOne(group.groupId()).orElse(null);

    compareGroups(instance, group);
  }

  @TestTemplate
  public void shouldSaveAndUpdate(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final GroupDbReader groupReader = rdbmsService.getGroupReader();

    final var group = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriters, group);

    final var groupUpdate =
        GroupFixtures.createRandomized(b -> b.groupId(group.groupId()).groupKey(group.groupKey()));
    rdbmsWriters.getGroupWriter().update(groupUpdate);
    rdbmsWriters.flush();

    final var instance = groupReader.findOne(group.groupId()).orElse(null);

    compareGroups(instance, groupUpdate);
  }

  @TestTemplate
  public void shouldSaveAndDelete(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final GroupDbReader groupReader = rdbmsService.getGroupReader();

    final var group = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriters, group);
    final var instance = groupReader.findOne(group.groupId()).orElse(null);
    compareGroups(instance, group);

    rdbmsWriters.getGroupWriter().delete(group.groupId());
    rdbmsWriters.flush();

    final var deletedInstance = groupReader.findOne(group.groupId()).orElse(null);
    assertThat(deletedInstance).isNull();
  }

  @TestTemplate
  public void shouldFindByName(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final GroupDbReader groupReader = rdbmsService.getGroupReader();

    final var group = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriters, group);

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
  public void shouldFindByGroupIds(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final GroupDbReader groupReader = rdbmsService.getGroupReader();

    final var group1 = GroupFixtures.createRandomized(b -> b);
    final var group2 = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriters, group1);
    createAndSaveGroup(rdbmsWriters, group2);

    final var searchResult =
        groupReader.search(
            new GroupQuery(
                new GroupFilter.Builder().groupIds(group1.groupId(), group2.groupId()).build(),
                GroupSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(GroupEntity::groupId).toList())
        .containsExactlyInAnyOrder(group1.groupId(), group2.groupId());
  }

  @TestTemplate
  public void shouldFindAllPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final GroupDbReader groupReader = rdbmsService.getGroupReader();

    createAndSaveRandomGroupsWithMembers(rdbmsWriters, b -> b.name("John Doe"));

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
  public void shouldFindAllPagedWithHasMoreHits(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final GroupDbReader groupReader = rdbmsService.getGroupReader();

    createAndSaveRandomGroupsWithMembers(rdbmsWriters, 120, b -> b.name("Jane More"));

    final var searchResult =
        groupReader.search(
            new GroupQuery(
                new GroupFilter.Builder().name("Jane More").build(),
                GroupSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(100);
    assertThat(searchResult.hasMoreTotalItems()).isEqualTo(true);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final GroupDbReader groupReader = rdbmsService.getGroupReader();

    final var group = GroupFixtures.createRandomized(b -> b);
    createAndSaveRandomGroups(rdbmsWriters);
    createAndSaveGroup(rdbmsWriters, group);

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

  @TestTemplate
  public void shouldFindWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final GroupDbReader groupReader = rdbmsService.getGroupReader();

    createAndSaveRandomGroups(rdbmsWriters, b -> b.name("Alice Doe"));
    final var sort = GroupSort.of(s -> s.name().asc().groupId().asc());
    final var searchResult =
        groupReader.search(
            GroupQuery.of(
                b -> b.filter(f -> f.name("Alice Doe")).sort(sort).page(p -> p.from(0).size(20))));

    final var firstPage =
        groupReader.search(
            GroupQuery.of(
                b -> b.filter(f -> f.name("Alice Doe")).sort(sort).page(p -> p.size(15))));

    final var nextPage =
        groupReader.search(
            GroupQuery.of(
                b ->
                    b.filter(f -> f.name("Alice Doe"))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(15, 20));
  }

  @TestTemplate
  public void shouldAddMemberToGroup(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final UserDbReader userReader = rdbmsService.getUserReader();
    final var group = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriters, group);
    final var user = UserFixtures.createRandomized(b -> b);
    createAndSaveUser(rdbmsWriters, user);

    // when
    rdbmsWriters
        .getGroupWriter()
        .addMember(new GroupMemberDbModel(group.groupId(), user.username(), "USER"));
    rdbmsWriters.flush();

    final var users =
        userReader.search(
            new UserQuery(
                new UserFilter.Builder().groupId(group.groupId()).build(),
                UserSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));
    assertThat(users.total()).isEqualTo(1);
  }

  @TestTemplate
  public void shouldRemoveMemberFromGroup(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final UserDbReader userReader = rdbmsService.getUserReader();
    final var group = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriters, group);
    final var user = UserFixtures.createRandomized(b -> b);
    createAndSaveUser(rdbmsWriters, user);
    rdbmsWriters
        .getGroupWriter()
        .addMember(new GroupMemberDbModel(group.groupId(), user.username(), "USER"));

    // when
    rdbmsWriters
        .getGroupWriter()
        .removeMember(new GroupMemberDbModel(group.groupId(), user.username(), "USER"));
    rdbmsWriters.flush();

    final var users =
        userReader.search(
            new UserQuery(
                new UserFilter.Builder().groupId(group.groupId()).build(),
                UserSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));
    assertThat(users.total()).isEqualTo(0);
  }

  private static void compareGroups(final GroupEntity instance, final GroupDbModel group) {
    assertThat(instance).isNotNull();
    assertThat(instance.groupKey()).isEqualTo(group.groupKey());
    assertThat(instance.groupId()).isEqualTo(group.groupId());
    assertThat(instance.name()).isEqualTo(group.name());
    assertThat(instance.description()).isEqualTo(group.description());
  }
}
