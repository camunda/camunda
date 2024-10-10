/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static io.camunda.db.rdbms.fixtures.ProcessInstanceFixtures.createAndSaveProcessDefinition;
import static io.camunda.db.rdbms.fixtures.ProcessInstanceFixtures.createAndSaveProcessInstance;
import static io.camunda.db.rdbms.fixtures.ProcessInstanceFixtures.createAndSaveRandomProcessInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.fixtures.ProcessDefinitionFixtures;
import io.camunda.db.rdbms.fixtures.ProcessInstanceFixtures;
import io.camunda.db.rdbms.read.domain.ProcessInstanceDbFilter;
import io.camunda.db.rdbms.read.service.ProcessInstanceReader;
import io.camunda.db.rdbms.util.TestConfiguration;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessInstanceSort;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJdbcTest
@ContextConfiguration(classes = {TestConfiguration.class, RdbmsConfiguration.class})
@AutoConfigurationPackage
@ActiveProfiles("test-h2")
public class ProcessInstanceSpecificFilterITest {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired
  private RdbmsService rdbmsService;

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  private RdbmsWriter rdbmsWriter;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriter = rdbmsService.createWriter();
  }

  @ParameterizedTest
  @MethodSource("shouldFindProcessInstanceWithSpecificFilterParameters")
  public void shouldFindProcessInstanceWithSpecificFilter(final ProcessInstanceFilter filter) {
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(1337L)
                    .bpmnProcessId("test-process")
                    .name("Test Process")
                    .versionTag("Version 1")));
    createAndSaveRandomProcessInstances(rdbmsWriter);
    createAndSaveProcessInstance(
        rdbmsWriter,
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processInstanceKey(42L)
                    .bpmnProcessId("test-process")
                    .processDefinitionKey(1337L)
                    .state(ProcessInstanceState.ACTIVE)
                    .startDate(NOW)
                    .endDate(NOW)
                    .parentProcessInstanceKey(-1L)
                    .parentElementInstanceKey(-1L)
                    .version(1)));

    final var searchResult = processInstanceReader.search(
                new ProcessInstanceDbFilter(
                    filter,
                    ProcessInstanceSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(42L);
  }

  static List<ProcessInstanceFilter> shouldFindProcessInstanceWithSpecificFilterParameters() {
    return List.of(
        new ProcessInstanceFilter.Builder().processInstanceKeys(42L).build(),
        new ProcessInstanceFilter.Builder().processDefinitionIds("test-process").build(),
        new ProcessInstanceFilter.Builder().processDefinitionKeys(1337L).build(),
        new ProcessInstanceFilter.Builder().states(ProcessInstanceState.ACTIVE.name()).build(),
        new ProcessInstanceFilter.Builder().parentProcessInstanceKeys(-1L).build(),
        new ProcessInstanceFilter.Builder().parentFlowNodeInstanceKeys(-1L).build(),
        new ProcessInstanceFilter.Builder().processDefinitionNames("Test Process").build(),
        new ProcessInstanceFilter.Builder().processDefinitionVersionTags("Version 1").build());
  }

}
