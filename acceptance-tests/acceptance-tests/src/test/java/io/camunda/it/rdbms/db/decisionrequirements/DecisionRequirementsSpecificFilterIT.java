/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.decisionrequirements;

import static io.camunda.it.rdbms.db.fixtures.DecisionRequirementsFixtures.createAndSaveDecisionRequirement;
import static io.camunda.it.rdbms.db.fixtures.DecisionRequirementsFixtures.createAndSaveRandomDecisionRequirements;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DecisionRequirementsReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.DecisionRequirementsFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.sort.DecisionRequirementsSort;
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
public class DecisionRequirementsSpecificFilterIT {

  @Autowired private RdbmsService rdbmsService;

  @Autowired private DecisionRequirementsReader decisionRequirementsReader;

  private RdbmsWriter rdbmsWriter;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriter = rdbmsService.createWriter(0L);
  }

  @ParameterizedTest
  @MethodSource("shouldFindWithSpecificFilterParameters")
  public void shouldFindWithSpecificFilter(final DecisionRequirementsFilter filter) {
    createAndSaveRandomDecisionRequirements(rdbmsWriter);
    createAndSaveDecisionRequirement(
        rdbmsWriter,
        DecisionRequirementsFixtures.createRandomized(
            b ->
                b.decisionRequirementsKey(1337L)
                    .decisionRequirementsId("sorting-test-requirement")
                    .name("Sorting Test Requirement")
                    .version(1337)
                    .tenantId("sorting-tenant1")));

    final var searchResult =
        decisionRequirementsReader.search(
            new DecisionRequirementsQuery(
                filter,
                DecisionRequirementsSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5)),
                null));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().decisionRequirementsKey()).isEqualTo(1337L);
  }

  static List<DecisionRequirementsFilter> shouldFindWithSpecificFilterParameters() {
    return List.of(
        new DecisionRequirementsFilter.Builder().decisionRequirementsKeys(1337L).build(),
        new DecisionRequirementsFilter.Builder()
            .decisionRequirementsIds("sorting-test-requirement")
            .build(),
        new DecisionRequirementsFilter.Builder().names("Sorting Test Requirement").build(),
        new DecisionRequirementsFilter.Builder().versions(1337).build(),
        new DecisionRequirementsFilter.Builder().tenantIds("sorting-tenant1").build());
  }
}
