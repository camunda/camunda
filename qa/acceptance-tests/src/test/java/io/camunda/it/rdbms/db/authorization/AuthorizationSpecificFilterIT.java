/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.authorization;

import static io.camunda.it.rdbms.db.fixtures.AuthorizationFixtures.createAndSaveAuthorization;
import static io.camunda.it.rdbms.db.fixtures.AuthorizationFixtures.createAndSaveRandomAuthorizations;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.AuthorizationDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.AuthorizationDbModel;
import io.camunda.it.rdbms.db.fixtures.AuthorizationFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.sort.AuthorizationSort;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@Tag("rdbms")
@DataJdbcTest
@ContextConfiguration(classes = {RdbmsTestConfiguration.class, RdbmsConfiguration.class})
@AutoConfigurationPackage
@TestPropertySource(
    properties = {"spring.liquibase.enabled=false", "camunda.data.secondary-storage.type=rdbms"})
public class AuthorizationSpecificFilterIT {

  @Autowired private RdbmsService rdbmsService;

  @Autowired private AuthorizationDbReader authorizationReader;

  private RdbmsWriters rdbmsWriters;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriters = rdbmsService.createWriter(0L);
  }

  @ParameterizedTest
  @MethodSource("shouldFindWithSpecificFilterParameters")
  public void shouldFindWithSpecificFilter(final AuthorizationFilter filter) {
    createAndSaveRandomAuthorizations(rdbmsWriters);
    createAndSaveAuthorization(
        rdbmsWriters,
        AuthorizationFixtures.createRandomized(
            b ->
                b.authorizationKey(100L)
                    .ownerId("foo")
                    .ownerType("FILTER_TEST")
                    .resourceType("TEST")));

    final var searchResult =
        authorizationReader.search(
            new AuthorizationQuery(
                filter, AuthorizationSort.of(b -> b), SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().ownerId()).isEqualTo("foo");
  }

  @ParameterizedTest
  @CsvSource({"USER, 1", "GROUP, 0"})
  public void shouldFindWithOwnerType(final EntityType ownerType, final int expectedCpount) {
    createAndSaveRandomAuthorizations(rdbmsWriters);
    createAndSaveAuthorization(
        rdbmsWriters,
        AuthorizationFixtures.createRandomized(
            b ->
                b.authorizationKey(100L)
                    .ownerId("foo")
                    .ownerType(EntityType.USER.name())
                    .resourceType("TEST")));

    final var searchResult =
        authorizationReader.search(
            new AuthorizationQuery(
                new AuthorizationFilter.Builder()
                    .ownerTypeToOwnerIds(Map.of(ownerType, Set.of("foo")))
                    .build(),
                AuthorizationSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(expectedCpount);
    assertThat(searchResult.items()).hasSize(expectedCpount);
  }

  @Test
  public void shouldFindByResourcePropertyName() {
    // given
    createAndSaveRandomAuthorizations(rdbmsWriters);
    final AuthorizationDbModel authDbModel =
        AuthorizationFixtures.createRandomized(
            b ->
                b.resourceMatcher(AuthorizationResourceMatcher.PROPERTY.value())
                    .resourcePropertyName("priority_prop"));

    createAndSaveAuthorization(rdbmsWriters, authDbModel);

    // when
    final var searchResult =
        authorizationReader.search(
            new AuthorizationQuery(
                new AuthorizationFilter.Builder().resourcePropertyNames("priority_prop").build(),
                AuthorizationSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    // then
    assertThat(searchResult.total()).isOne();
    assertThat(searchResult.items())
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.authorizationKey()).isEqualTo(authDbModel.authorizationKey());
              assertThat(a.resourceMatcher())
                  .isEqualTo(AuthorizationResourceMatcher.PROPERTY.value());
              assertThat(a.resourcePropertyName()).isEqualTo("priority_prop");
            });
  }

  static List<AuthorizationFilter> shouldFindWithSpecificFilterParameters() {
    return List.of(
        new AuthorizationFilter.Builder().authorizationKey(100L).build(),
        new AuthorizationFilter.Builder().ownerIds("foo").build(),
        new AuthorizationFilter.Builder().ownerType("FILTER_TEST").build(),
        new AuthorizationFilter.Builder().resourceType("TEST").build());
  }
}
