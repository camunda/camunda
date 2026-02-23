/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processinstance;

import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveProcessDefinition;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveProcessInstance;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveRandomProcessInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.filter.ProcessInstanceFilter.Builder;
import io.camunda.search.query.ProcessInstanceQuery;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
    properties = {
      "spring.liquibase.enabled=false",
      "camunda.data.secondary-storage.type=rdbms",
      "logging.level.io.camunda.db.rdbms.sql=DEBUG"
    })
public class ProcessInstanceSpecificFilterIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired private RdbmsService rdbmsService;

  @Autowired private ProcessInstanceDbReader processInstanceReader;

  private RdbmsWriters rdbmsWriters;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriters = rdbmsService.createWriter(0L);
  }

  @ParameterizedTest
  @MethodSource("shouldFindProcessInstanceWithSpecificFilterParameters")
  public void shouldFindProcessInstanceWithSpecificFilter(
      final ProcessInstanceFilter filter,
      final int expectedTotal,
      final int expectedItemsSize,
      final List<Long> expectedKeys) {
    createAndSaveProcessDefinition(
        rdbmsWriters,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(987654321L)
                    .processDefinitionId("test-process-987654321")
                    .name("Test Process 987654321")
                    .versionTag("Version 1")));
    createAndSaveRandomProcessInstances(rdbmsWriters, b -> b.state(ProcessInstanceState.COMPLETED));
    createAndSaveProcessInstance(
        rdbmsWriters,
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processInstanceKey(42L)
                    .processDefinitionId("test-process-987654321")
                    .processDefinitionKey(987654321L)
                    .state(ProcessInstanceState.ACTIVE)
                    .startDate(NOW)
                    .endDate(NOW)
                    .parentProcessInstanceKey(-1L)
                    .parentElementInstanceKey(-1L)
                    .version(1)));
    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b -> b.filter(filter).sort(s -> s).page(p -> p.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(expectedTotal);
    assertThat(searchResult.items()).hasSize(expectedItemsSize);
    assertThat(
            searchResult.items().stream().map(ProcessInstanceEntity::processInstanceKey).toList())
        .contains(expectedKeys.toArray(new Long[0]));
  }

  static Stream<Arguments> shouldFindProcessInstanceWithSpecificFilterParameters() {
    return Stream.of(
        Arguments.of(
            new ProcessInstanceFilter.Builder().processInstanceKeys(42L).build(),
            1,
            1,
            List.of(42L)),
        Arguments.of(
            new ProcessInstanceFilter.Builder()
                .processDefinitionIds("test-process-987654321")
                .build(),
            1,
            1,
            List.of(42L)),
        Arguments.of(
            new ProcessInstanceFilter.Builder().processDefinitionKeys(987654321L).build(),
            1,
            1,
            List.of(42L)),
        Arguments.of(
            new ProcessInstanceFilter.Builder().states(ProcessInstanceState.ACTIVE.name()).build(),
            1,
            1,
            List.of(42L)),
        Arguments.of(
            new ProcessInstanceFilter.Builder().parentProcessInstanceKeys(-1L).build(),
            1,
            1,
            List.of(42L)),
        Arguments.of(
            new ProcessInstanceFilter.Builder().parentFlowNodeInstanceKeys(-1L).build(),
            1,
            1,
            List.of(42L)),
        Arguments.of(
            new ProcessInstanceFilter.Builder()
                .processDefinitionNames("Test Process 987654321")
                .build(),
            1,
            1,
            List.of(42L)),
        Arguments.of(
            new ProcessInstanceFilter.Builder().processDefinitionVersionTags("Version 1").build(),
            1,
            1,
            List.of(42L)),
        Arguments.of(
            new ProcessInstanceFilter.Builder()
                .processInstanceKeys(42L)
                .orFilters(
                    List.of(new Builder().states(ProcessInstanceState.ACTIVE.name()).build()))
                .build(),
            1,
            1,
            List.of(42L)),
        Arguments.of(
            new ProcessInstanceFilter.Builder()
                .orFilters(
                    List.of(
                        new Builder().states(ProcessInstanceState.ACTIVE.name()).build(),
                        new Builder().states(ProcessInstanceState.COMPLETED.name()).build()))
                .build(),
            21,
            5,
            List.of(42L)));
  }
}
