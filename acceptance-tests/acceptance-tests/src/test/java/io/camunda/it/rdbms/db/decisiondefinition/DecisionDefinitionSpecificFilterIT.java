/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.decisiondefinition;

import static io.camunda.it.rdbms.db.fixtures.DecisionDefinitionFixtures.createAndSaveDecisionDefinition;
import static io.camunda.it.rdbms.db.fixtures.DecisionDefinitionFixtures.createAndSaveRandomDecisionDefinitions;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DecisionDefinitionReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.DecisionDefinitionFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.sort.DecisionDefinitionSort;
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
public class DecisionDefinitionSpecificFilterIT {

  @Autowired private RdbmsService rdbmsService;

  @Autowired private DecisionDefinitionReader decisionDefinitionReader;

  private RdbmsWriter rdbmsWriter;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriter = rdbmsService.createWriter(0L);
  }

  @ParameterizedTest
  @MethodSource("shouldFindWithSpecificFilterParameters")
  public void shouldFindWithSpecificFilter(final DecisionDefinitionFilter filter) {
    createAndSaveRandomDecisionDefinitions(rdbmsWriter);
    createAndSaveDecisionDefinition(
        rdbmsWriter,
        DecisionDefinitionFixtures.createRandomized(
            b ->
                b.decisionDefinitionKey(1337L)
                    .decisionDefinitionId("sorting-test-process")
                    .name("Sorting Test Process")
                    .version(1337)
                    .decisionRequirementsKey(1338L)
                    .decisionRequirementsId("requirements-1338")
                    .tenantId("sorting-tenant1")));

    final var searchResult =
        decisionDefinitionReader.search(
            new DecisionDefinitionQuery(
                filter,
                DecisionDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().decisionDefinitionKey()).isEqualTo(1337L);
  }

  static List<DecisionDefinitionFilter> shouldFindWithSpecificFilterParameters() {
    return List.of(
        new DecisionDefinitionFilter.Builder().decisionDefinitionKeys(1337L).build(),
        new DecisionDefinitionFilter.Builder()
            .decisionDefinitionIds("sorting-test-process")
            .build(),
        new DecisionDefinitionFilter.Builder().names("Sorting Test Process").build(),
        new DecisionDefinitionFilter.Builder().decisionRequirementsIds("requirements-1338").build(),
        new DecisionDefinitionFilter.Builder().decisionRequirementsKeys(1338L).build(),
        new DecisionDefinitionFilter.Builder().versions(1337).build(),
        new DecisionDefinitionFilter.Builder().tenantIds("sorting-tenant1").build());
  }
}
