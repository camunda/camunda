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
import io.camunda.db.rdbms.read.service.AuthorizationReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.AuthorizationFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.sort.AuthorizationSort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
public class AuthorizationSpecificFilterIT {

  @Autowired private RdbmsService rdbmsService;

  @Autowired private AuthorizationReader authorizationReader;

  private RdbmsWriter rdbmsWriter;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriter = rdbmsService.createWriter(0L);
  }

  @ParameterizedTest
  @MethodSource("shouldFindWithSpecificFilterParameters")
  public void shouldFindWithSpecificFilter(final AuthorizationFilter filter) {
    createAndSaveRandomAuthorizations(rdbmsWriter);
    createAndSaveAuthorization(
        rdbmsWriter,
        AuthorizationFixtures.createRandomized(
            b -> b.ownerId("foo").ownerType("FILTER_TEST").resourceType("TEST")));

    final var searchResult =
        authorizationReader.search(
            new AuthorizationQuery(
                filter, AuthorizationSort.of(b -> b), SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().ownerId()).isEqualTo("foo");
  }

  static List<AuthorizationFilter> shouldFindWithSpecificFilterParameters() {
    return List.of(
        new AuthorizationFilter.Builder().ownerIds("foo").build(),
        new AuthorizationFilter.Builder().ownerType("FILTER_TEST").build(),
        new AuthorizationFilter.Builder().resourceType("TEST").build());
  }
}
