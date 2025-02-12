/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.user;

import static io.camunda.it.rdbms.db.fixtures.TenantFixtures.createAndSaveTenant;
import static io.camunda.it.rdbms.db.fixtures.UserFixtures.createAndSaveRandomUsers;
import static io.camunda.it.rdbms.db.fixtures.UserFixtures.createAndSaveUser;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.UserReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.TenantMemberDbModel;
import io.camunda.it.rdbms.db.fixtures.TenantFixtures;
import io.camunda.it.rdbms.db.fixtures.UserFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.UserQuery;
import io.camunda.search.sort.UserSort;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@Tag("rdbms")
@DataJdbcTest
@ContextConfiguration(classes = {RdbmsTestConfiguration.class, RdbmsConfiguration.class})
@AutoConfigurationPackage
@TestPropertySource(properties = {"spring.liquibase.enabled=false", "camunda.database.type=rdbms"})
public class UserSpecificFilterIT {

  @Autowired private RdbmsService rdbmsService;

  @Autowired private UserReader userReader;

  private RdbmsWriter rdbmsWriter;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriter = rdbmsService.createWriter(0L);
  }

  @Test
  public void shouldFilterUsersForTenant() {
    final var tenant = TenantFixtures.createRandomized(b -> b);
    final var anotherTenant = TenantFixtures.createRandomized(b -> b);
    createAndSaveTenant(rdbmsWriter, tenant);
    createAndSaveTenant(rdbmsWriter, anotherTenant);

    Arrays.asList("user-abc", "user-123", "user-1337")
        .forEach(
            username ->
                createAndSaveUser(
                    rdbmsWriter,
                    UserFixtures.createRandomized(
                        b ->
                            b.name("User 1337")
                                .username(username)
                                .email(username + "@camunda-test.com"))));

    addUserToTenant(tenant.tenantId(), "user-1337");
    addUserToTenant(anotherTenant.tenantId(), "user-abc");
    addUserToTenant(anotherTenant.tenantId(), "user-123");

    final var users =
        userReader.search(
            new UserQuery(
                new UserFilter.Builder().tenantId(tenant.tenantId()).build(),
                UserSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(users.total()).isEqualTo(1);
  }

  @ParameterizedTest
  @MethodSource("shouldFindWithSpecificFilterParameters")
  public void shouldFindWithSpecificFilter(final UserFilter filter) {
    createAndSaveRandomUsers(rdbmsWriter);
    createAndSaveUser(
        rdbmsWriter,
        UserFixtures.createRandomized(
            b ->
                b.userKey(1337L)
                    .name("User 1337")
                    .username("user-1337")
                    .email("user-1337@camunda-test.com")));

    final var searchResult =
        userReader.search(
            new UserQuery(filter, UserSort.of(b -> b), SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().userKey()).isEqualTo(1337L);
  }

  static List<UserFilter> shouldFindWithSpecificFilterParameters() {
    return List.of(
        new UserFilter.Builder().key(1337L).build(),
        new UserFilter.Builder().username("user-1337").build(),
        new UserFilter.Builder().name("User 1337").build(),
        new UserFilter.Builder().email("user-1337@camunda-test.com").build());
  }

  private void addUserToTenant(final String tenantId, final String entityId) {
    rdbmsWriter.getTenantWriter().addMember(new TenantMemberDbModel(tenantId, entityId, "USER"));
    rdbmsWriter.flush();
  }
}
