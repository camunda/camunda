/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.role;

import static io.camunda.it.rdbms.db.fixtures.RoleFixtures.createAndSaveRandomRoles;
import static io.camunda.it.rdbms.db.fixtures.RoleFixtures.createAndSaveRandomRolesWithMembers;
import static io.camunda.it.rdbms.db.fixtures.RoleFixtures.createAndSaveRole;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.RoleDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.RoleDbModel;
import io.camunda.it.rdbms.db.fixtures.RoleFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.filter.RoleFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.sort.RoleSort;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class RoleIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindById(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final RoleDbReader roleReader = rdbmsService.getRoleReader();

    final var role = RoleFixtures.createRandomized(b -> b);
    createAndSaveRole(rdbmsWriters, role);

    final var instance = roleReader.findOne(role.roleId()).orElse(null);

    compareRoles(instance, role);
  }

  @TestTemplate
  public void shouldSaveAndUpdate(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final RoleDbReader roleReader = rdbmsService.getRoleReader();

    final var role = RoleFixtures.createRandomized(b -> b);
    createAndSaveRole(rdbmsWriters, role);

    final var roleUpdate =
        RoleFixtures.createRandomized(b -> b.roleId(role.roleId()).roleKey(role.roleKey()));
    rdbmsWriters.getRoleWriter().update(roleUpdate);
    rdbmsWriters.flush();

    final var instance = roleReader.findOne(role.roleId()).orElse(null);

    compareRoles(instance, roleUpdate);
  }

  @TestTemplate
  public void shouldSaveAndDelete(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final RoleDbReader roleReader = rdbmsService.getRoleReader();

    final var role = RoleFixtures.createRandomized(b -> b);
    createAndSaveRole(rdbmsWriters, role);
    final var instance = roleReader.findOne(role.roleId()).orElse(null);
    compareRoles(instance, role);

    rdbmsWriters.getRoleWriter().delete(role.roleId());
    rdbmsWriters.flush();

    final var deletedInstance = roleReader.findOne(role.roleId()).orElse(null);
    assertThat(deletedInstance).isNull();
  }

  @TestTemplate
  public void shouldFindByName(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final RoleDbReader roleReader = rdbmsService.getRoleReader();

    final var role = RoleFixtures.createRandomized(b -> b);
    createAndSaveRole(rdbmsWriters, role);

    final var searchResult =
        roleReader.search(
            new RoleQuery(
                new RoleFilter.Builder().name(role.name()).build(),
                RoleSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(instance).isNotNull();
    assertThat(instance.roleKey()).isEqualTo(role.roleKey());
    assertThat(instance.name()).isEqualTo(role.name());
  }

  @TestTemplate
  public void shouldFindAllPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final RoleDbReader roleReader = rdbmsService.getRoleReader();

    createAndSaveRandomRolesWithMembers(rdbmsWriters, b -> b.name("John Doe"));

    final var searchResult =
        roleReader.search(
            new RoleQuery(
                new RoleFilter.Builder().name("John Doe").build(),
                RoleSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllPagedWithHasMoreHits(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final RoleDbReader roleReader = rdbmsService.getRoleReader();

    createAndSaveRandomRolesWithMembers(rdbmsWriters, 120, b -> b.name("Jane More"));

    final var searchResult =
        roleReader.search(
            new RoleQuery(
                new RoleFilter.Builder().name("Jane More").build(),
                RoleSort.of(b -> b),
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
    final RoleDbReader roleReader = rdbmsService.getRoleReader();

    final var role = RoleFixtures.createRandomized(b -> b);
    createAndSaveRandomRoles(rdbmsWriters);
    createAndSaveRole(rdbmsWriters, role);

    final var searchResult =
        roleReader.search(
            new RoleQuery(
                new RoleFilter.Builder().roleId(role.roleId()).name(role.name()).build(),
                RoleSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().roleId()).isEqualTo(role.roleId());
  }

  @TestTemplate
  public void shouldFindWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final RoleDbReader roleReader = rdbmsService.getRoleReader();

    createAndSaveRandomRoles(rdbmsWriters, b -> b.name("Alice Doe"));
    final var sort = RoleSort.of(s -> s.name().asc());
    final var searchResult =
        roleReader.search(
            RoleQuery.of(
                b -> b.filter(f -> f.name("Alice Doe")).sort(sort).page(p -> p.from(0).size(20))));

    final var firstPage =
        roleReader.search(
            RoleQuery.of(b -> b.filter(f -> f.name("Alice Doe")).sort(sort).page(p -> p.size(15))));

    final var nextPage =
        roleReader.search(
            RoleQuery.of(
                b ->
                    b.filter(f -> f.name("Alice Doe"))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(15, 20));
  }

  private static void compareRoles(final RoleEntity instance, final RoleDbModel role) {
    assertThat(instance).isNotNull();
    assertThat(instance)
        .usingRecursiveComparison()
        .ignoringFields("assignedMemberIds")
        .isEqualTo(role);
  }
}
