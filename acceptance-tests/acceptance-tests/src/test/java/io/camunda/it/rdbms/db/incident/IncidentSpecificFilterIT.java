/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.incident;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.NOW;
import static io.camunda.it.rdbms.db.fixtures.IncidentFixtures.createAndSaveIncident;
import static io.camunda.it.rdbms.db.fixtures.IncidentFixtures.createAndSaveRandomIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.IncidentReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.fixtures.IncidentFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.filter.DateValueFilter;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.sort.IncidentSort;
import java.time.temporal.ChronoUnit;
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
public class IncidentSpecificFilterIT {

  @Autowired private RdbmsService rdbmsService;

  @Autowired private IncidentReader processDefinitionReader;

  private RdbmsWriter rdbmsWriter;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriter = rdbmsService.createWriter(0L);
  }

  @ParameterizedTest
  @MethodSource("shouldFindIncidentWithSpecificFilterParameters")
  public void shouldFindIncidentWithSpecificFilter(final IncidentFilter filter) {
    createAndSaveRandomIncidents(
        rdbmsWriter, b -> b.errorType(ErrorType.CONDITION_ERROR).state(IncidentState.RESOLVED));
    createAndSaveIncident(
        rdbmsWriter,
        IncidentFixtures.createRandomized(
            b ->
                b.incidentKey(1337L)
                    .processDefinitionKey(2000L)
                    .processDefinitionId("sorting-test-process")
                    .processInstanceKey(3000L)
                    .flowNodeId("sorting-flow-node")
                    .flowNodeInstanceKey(4000L)
                    .errorType(ErrorType.JOB_NO_RETRIES)
                    .errorMessage("error-message-5000")
                    .state(IncidentState.ACTIVE)
                    .jobKey(6000L)
                    .creationDate(CommonFixtures.NOW)
                    .tenantId("sorting-tenant1")));

    final var searchResult =
        processDefinitionReader.search(
            new IncidentQuery(
                filter, IncidentSort.of(b -> b), SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().incidentKey()).isEqualTo(1337L);
  }

  static List<IncidentFilter> shouldFindIncidentWithSpecificFilterParameters() {
    return List.of(
        new IncidentFilter.Builder().incidentKeys(1337L).build(),
        new IncidentFilter.Builder().processDefinitionKeys(2000L).build(),
        new IncidentFilter.Builder().processDefinitionIds("sorting-test-process").build(),
        new IncidentFilter.Builder().processInstanceKeys(3000L).build(),
        new IncidentFilter.Builder().flowNodeIds("sorting-flow-node").build(),
        new IncidentFilter.Builder().flowNodeInstanceKeys(4000L).build(),
        new IncidentFilter.Builder().errorTypes(ErrorType.JOB_NO_RETRIES).build(),
        new IncidentFilter.Builder().errorMessages("error-message-5000").build(),
        new IncidentFilter.Builder().states(IncidentState.ACTIVE).build(),
        new IncidentFilter.Builder().jobKeys(6000L).build(),
        new IncidentFilter.Builder()
            .creationTime(
                new DateValueFilter(
                    NOW.minus(1, ChronoUnit.MILLIS), NOW.plus(1, ChronoUnit.MILLIS)))
            .build(),
        new IncidentFilter.Builder().tenantIds("sorting-tenant1").build());
  }
}
