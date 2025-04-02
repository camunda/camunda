/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.decisioninstance;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures.createAndSaveDecisionInstance;
import static io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures.createAndSaveRandomDecisionInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DecisionInstanceReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.DecisionDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.sort.DecisionInstanceSort;
import java.time.OffsetDateTime;
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
public class DecisionInstanceSpecificFilterIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired private RdbmsService rdbmsService;

  @Autowired private DecisionInstanceReader decisionInstanceReader;

  private RdbmsWriter rdbmsWriter;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriter = rdbmsService.createWriter(0L);

    final var decisionDefinitionKey = nextKey();
    final var decisionDefinition =
        DecisionDefinitionFixtures.createAndSaveDecisionDefinition(
            rdbmsWriter,
            b ->
                b.decisionDefinitionKey(decisionDefinitionKey)
                    .decisionDefinitionId("decision" + decisionDefinitionKey)
                    .name("Decision " + decisionDefinitionKey));
    createAndSaveRandomDecisionInstances(
        rdbmsWriter,
        b ->
            b.state(DecisionInstanceState.UNSPECIFIED)
                .decisionType(DecisionDefinitionType.UNSPECIFIED)
                .decisionDefinitionKey(decisionDefinition.decisionDefinitionKey())
                .decisionDefinitionId(decisionDefinition.decisionDefinitionId()));
  }

  @ParameterizedTest
  @MethodSource("shouldFindDecisionInstanceWithSpecificFilterParameters")
  public void shouldFindDecisionInstanceWithSpecificFilter(final DecisionInstanceFilter filter) {
    final var decisionDefinitionKey = 100L;

    final var decisionDefinition =
        DecisionDefinitionFixtures.createAndSaveDecisionDefinition(
            rdbmsWriter,
            b ->
                b.decisionDefinitionKey(decisionDefinitionKey)
                    .decisionDefinitionId("decision-" + decisionDefinitionKey)
                    .name("Decision " + decisionDefinitionKey));
    createAndSaveDecisionInstance(
        rdbmsWriter,
        DecisionInstanceFixtures.createRandomized(
            b ->
                b.decisionInstanceId("42-1")
                    .decisionInstanceKey(42L)
                    .flowNodeId("unique-flowNode-42")
                    .processInstanceKey(123L)
                    .processDefinitionId("unique-process-124")
                    .processDefinitionKey(124L)
                    .state(DecisionInstanceState.EVALUATED)
                    .decisionType(DecisionDefinitionType.DECISION_TABLE)
                    .decisionDefinitionKey(decisionDefinition.decisionDefinitionKey())
                    .decisionDefinitionId(decisionDefinition.decisionDefinitionId())
                    .evaluationFailure("failure-42")
                    .result("result-42")));

    final var searchResult =
        decisionInstanceReader.search(
            new DecisionInstanceQuery(
                filter,
                DecisionInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5)),
                null));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().decisionInstanceId()).isEqualTo("42-1");
  }

  static List<DecisionInstanceFilter> shouldFindDecisionInstanceWithSpecificFilterParameters() {
    return List.of(
        DecisionInstanceFilter.of(b -> b.decisionInstanceIds("42-1")),
        DecisionInstanceFilter.of(b -> b.decisionInstanceKeys(42L)),
        DecisionInstanceFilter.of(b -> b.processInstanceKeys(123L)),
        DecisionInstanceFilter.of(b -> b.processDefinitionKeys(124L)),
        DecisionInstanceFilter.of(
            b -> b.decisionDefinitionKeyOperations(List.of(Operation.eq(100L)))),
        DecisionInstanceFilter.of(b -> b.decisionDefinitionIds("decision-100")),
        DecisionInstanceFilter.of(b -> b.states(DecisionInstanceState.EVALUATED)),
        DecisionInstanceFilter.of(b -> b.decisionTypes(DecisionDefinitionType.DECISION_TABLE)),
        DecisionInstanceFilter.of(b -> b.evaluationFailures("failure-42")),
        DecisionInstanceFilter.of(b -> b.decisionDefinitionNames("Decision 100")));
  }
}
