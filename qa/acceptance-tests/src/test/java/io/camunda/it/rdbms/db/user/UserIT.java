/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.user;

import static io.camunda.it.rdbms.db.fixtures.UserFixtures.createAndSaveRandomUsers;
import static io.camunda.it.rdbms.db.fixtures.UserFixtures.createAndSaveUser;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.UserDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.UserDbModel;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.fixtures.UserFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.UserQuery;
import io.camunda.search.sort.UserSort;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class UserIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final UserDbReader userReader = rdbmsService.getUserReader();

    final var user = UserFixtures.createRandomized(b -> b);
    createAndSaveUser(rdbmsWriters, user);

    final var instance = userReader.findOne(user.userKey()).orElse(null);

    compareUsers(instance, user);
  }

  @TestTemplate
  public void shouldSaveAndUpdate(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final UserDbReader userReader = rdbmsService.getUserReader();

    final var user = UserFixtures.createRandomized(b -> b);
    createAndSaveUser(rdbmsWriters, user);

    final var userUpdate =
        UserFixtures.createRandomized(b -> b.userKey(user.userKey()).username(user.username()));
    rdbmsWriters.getUserWriter().update(userUpdate);
    rdbmsWriters.flush();

    final var instance = userReader.findOne(user.userKey()).orElse(null);

    compareUsers(instance, userUpdate);
  }

  @TestTemplate
  public void shouldSaveAndDelete(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final UserDbReader userReader = rdbmsService.getUserReader();

    final var user = UserFixtures.createRandomized(b -> b);
    createAndSaveUser(rdbmsWriters, user);
    final var instance = userReader.findOne(user.userKey()).orElse(null);
    compareUsers(instance, user);

    rdbmsWriters.getUserWriter().delete(user.username());
    rdbmsWriters.flush();

    final var deletedInstance = userReader.findOne(user.userKey()).orElse(null);
    assertThat(deletedInstance).isNull();
  }

  @TestTemplate
  public void shouldFindByUsername(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final UserDbReader userReader = rdbmsService.getUserReader();

    final var user = UserFixtures.createRandomized(b -> b);
    createAndSaveUser(rdbmsWriters, user);

    final var searchResult =
        userReader.search(
            new UserQuery(
                new UserFilter.Builder().usernames(user.username()).build(),
                UserSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(instance).isNotNull();
    assertThat(instance.userKey()).isEqualTo(user.userKey());
    assertThat(instance.username()).isEqualTo(user.username());
    assertThat(instance.name()).isEqualTo(user.name());
    assertThat(instance.email()).isEqualTo(user.email());
  }

  @TestTemplate
  public void shouldFindAuthorization(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final UserDbReader userReader = rdbmsService.getUserReader();

    final var user = UserFixtures.createRandomized(b -> b);
    createAndSaveUser(rdbmsWriters, user);
    createAndSaveRandomUsers(rdbmsWriters, b -> b.email("john.doe@camunda.com"));

    final var searchResult =
        userReader.search(
            UserQuery.of(b -> b),
            CommonFixtures.resourceAccessChecksFromResourceIds(
                AuthorizationResourceType.USER, user.username()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(instance).isNotNull();
    assertThat(instance.userKey()).isEqualTo(user.userKey());
    assertThat(instance.username()).isEqualTo(user.username());
    assertThat(instance.name()).isEqualTo(user.name());
    assertThat(instance.email()).isEqualTo(user.email());
  }

  @TestTemplate
  public void shouldFindAllPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final UserDbReader userReader = rdbmsService.getUserReader();

    final String userId = UserFixtures.nextStringId();
    createAndSaveRandomUsers(rdbmsWriters, b -> b.name("John Doe"));

    final var searchResult =
        userReader.search(
            new UserQuery(
                new UserFilter.Builder().names("John Doe").build(),
                UserSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllPagedWithHasMoreHits(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final UserDbReader userReader = rdbmsService.getUserReader();

    createAndSaveRandomUsers(rdbmsWriters, 120, b -> b.name("Jane More"));

    final var searchResult =
        userReader.search(
            new UserQuery(
                new UserFilter.Builder().names("Jane More").build(),
                UserSort.of(b -> b),
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
    final UserDbReader userReader = rdbmsService.getUserReader();

    final var user = UserFixtures.createRandomized(b -> b);
    createAndSaveRandomUsers(rdbmsWriters);
    createAndSaveUser(rdbmsWriters, user);

    final var searchResult =
        userReader.search(
            new UserQuery(
                new UserFilter.Builder()
                    .key(user.userKey())
                    .usernames(user.username())
                    .names(user.name())
                    .emails(user.email())
                    .build(),
                UserSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().userKey()).isEqualTo(user.userKey());
  }

  @TestTemplate
  public void shouldFindWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final UserDbReader userReader = rdbmsService.getUserReader();

    createAndSaveRandomUsers(rdbmsWriters, b -> b.name("Alice Doe"));
    final var sort = UserSort.of(s -> s.name().asc().username().asc().email().desc());
    final var searchResult =
        userReader.search(
            UserQuery.of(
                b -> b.filter(f -> f.names("Alice Doe")).sort(sort).page(p -> p.from(0).size(20))));

    final var firstPage =
        userReader.search(
            UserQuery.of(
                b -> b.filter(f -> f.names("Alice Doe")).sort(sort).page(p -> p.size(15))));

    final var nextPage =
        userReader.search(
            UserQuery.of(
                b ->
                    b.filter(f -> f.names("Alice Doe"))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(15, 20));
  }

  private static void compareUsers(final UserEntity instance, final UserDbModel user) {
    assertThat(instance).isNotNull();
    assertThat(instance).usingRecursiveComparison().isEqualTo(user);
  }
}
