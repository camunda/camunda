/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.domain.ProcessInstanceDbFilter;
import io.camunda.db.rdbms.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.fixtures.ProcessDefinitionFixtures;
import io.camunda.db.rdbms.fixtures.ProcessInstanceFixtures;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessInstanceSort;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
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
public class ProcessInstanceITest {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired
  private RdbmsService rdbmsService;

  @Test
  public void shouldSaveAndFindProcessInstanceByKey() {
    createAndSaveProcessInstance(ProcessInstanceFixtures.createRandomized(b -> b
        .processInstanceKey(42L)
        .bpmnProcessId("test-process")
        .processDefinitionKey(1337L)
        .state(ProcessInstanceState.ACTIVE)
        .startDate(NOW)
        .parentProcessInstanceKey(-1L)
        .parentElementInstanceKey(-1L)
        .version(1)
    ));

    var instance = rdbmsService.getProcessInstanceRdbmsService().findOne(42L);

    assertThat(instance).isNotNull();
    assertThat(instance.key()).isEqualTo(42L);
    assertThat(instance.bpmnProcessId()).isEqualTo("test-process");
    assertThat(instance.processDefinitionKey()).isEqualTo(1337L);
    assertThat(instance.state()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(instance.startDate()).isEqualTo(NOW.toString());
    assertThat(instance.parentProcessInstanceKey()).isEqualTo(-1L);
    assertThat(instance.parentFlowNodeInstanceKey()).isEqualTo(-1L);
    assertThat(instance.processVersion()).isEqualTo(1);
  }

  @Test
  public void shouldFindProcessInstanceByBpmnProcessId() {
    createAndSaveProcessInstance(ProcessInstanceFixtures.createRandomized(b -> b
        .processInstanceKey(42L)
        .bpmnProcessId("test-process")
        .processDefinitionKey(1337L)
        .state(ProcessInstanceState.ACTIVE)
        .startDate(NOW)
        .parentProcessInstanceKey(-1L)
        .parentElementInstanceKey(-1L)
        .version(1)
    ));

    var searchResult = rdbmsService.getProcessInstanceRdbmsService().search(new ProcessInstanceDbFilter(
        new ProcessInstanceFilter.Builder().processDefinitionIds("test-process").build(),
        ProcessInstanceSort.of(b -> b),
        SearchQueryPage.of(b -> b.from(0).size(10))
    ));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);

    var instance = searchResult.hits().getFirst();

    assertThat(instance.key()).isEqualTo(42L);
    assertThat(instance.bpmnProcessId()).isEqualTo("test-process");
    assertThat(instance.processDefinitionKey()).isEqualTo(1337L);
    assertThat(instance.state()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(instance.startDate()).isEqualTo(NOW.toString());
    assertThat(instance.parentProcessInstanceKey()).isEqualTo(-1L);
    assertThat(instance.parentFlowNodeInstanceKey()).isEqualTo(-1L);
    assertThat(instance.processVersion()).isEqualTo(1);
  }

  @Test
  public void shouldFindAllProcessInstancePaged() {
    createAndSaveRandomProcessInstances();

    var searchResult = rdbmsService.getProcessInstanceRdbmsService().search(new ProcessInstanceDbFilter(
        new ProcessInstanceFilter.Builder().build(),
        ProcessInstanceSort.of(b -> b),
        SearchQueryPage.of(b -> b.from(0).size(5))
    ));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(5);
  }

  @Test
  public void shouldFindProcessInstanceWithFullFilter() {
    createAndSaveRandomProcessInstances();
    createAndSaveProcessInstance(ProcessInstanceFixtures.createRandomized(b -> b
        .processInstanceKey(42L)
        .bpmnProcessId("test-process")
        .processDefinitionKey(1337L)
        .state(ProcessInstanceState.ACTIVE)
        .startDate(NOW)
        .endDate(NOW)
        .parentProcessInstanceKey(-1L)
        .parentElementInstanceKey(-1L)
        .version(1)
    ));

    var searchResult = rdbmsService.getProcessInstanceRdbmsService().search(new ProcessInstanceDbFilter(
        new ProcessInstanceFilter.Builder()
            .processInstanceKeys(42L)
            .processDefinitionIds("test-process")
            .processDefinitionKeys(1337L)
            .states(ProcessInstanceState.ACTIVE.name())
            .parentProcessInstanceKeys(-1L)
            .parentFlowNodeInstanceKeys(-1L)
            .build(),
        ProcessInstanceSort.of(b -> b),
        SearchQueryPage.of(b -> b.from(0).size(5))
    ));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(42L);
  }

  @ParameterizedTest
  @MethodSource("shouldFindProcessInstanceWithSpecificFilterParameters")
  public void shouldFindProcessInstanceWithSpecificFilter(ProcessInstanceFilter filter) {
    createAndSaveProcessDefinition(ProcessDefinitionFixtures.createRandomized(b -> b
        .processDefinitionKey(1337L)
        .bpmnProcessId("test-process")
        .name("Test Process")
        .versionTag("Version 1")
    ));
    createAndSaveRandomProcessInstances();
    createAndSaveProcessInstance(ProcessInstanceFixtures.createRandomized(b -> b
        .processInstanceKey(42L)
        .bpmnProcessId("test-process")
        .processDefinitionKey(1337L)
        .state(ProcessInstanceState.ACTIVE)
        .startDate(NOW)
        .endDate(NOW)
        .parentProcessInstanceKey(-1L)
        .parentElementInstanceKey(-1L)
        .version(1)
    ));

    var searchResult = rdbmsService.getProcessInstanceRdbmsService().search(new ProcessInstanceDbFilter(
        filter,
        ProcessInstanceSort.of(b -> b),
        SearchQueryPage.of(b -> b.from(0).size(5))
    ));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(42L);
  }

  static List<ProcessInstanceFilter> shouldFindProcessInstanceWithSpecificFilterParameters() {
    return List.of(
        new ProcessInstanceFilter.Builder()
            .processInstanceKeys(42L)
            .build(),
        new ProcessInstanceFilter.Builder()
            .processDefinitionIds("test-process")
            .build(),
        new ProcessInstanceFilter.Builder()
            .processDefinitionKeys(1337L)
            .build(),
        new ProcessInstanceFilter.Builder()
            .states(ProcessInstanceState.ACTIVE.name())
            .build(),
        new ProcessInstanceFilter.Builder()
            .parentProcessInstanceKeys(-1L)
            .build(),
        new ProcessInstanceFilter.Builder()
            .parentFlowNodeInstanceKeys(-1L)
            .build(),
        new ProcessInstanceFilter.Builder()
            .processDefinitionNames("Test Process")
            .build(),
        new ProcessInstanceFilter.Builder()
            .processDefinitionVersionTags("Version 1")
            .build()
    );
  }

  private void createAndSaveProcessDefinition(ProcessDefinitionDbModel processDefinition) {
    rdbmsService.getProcessDefinitionRdbmsService().save(processDefinition);
    rdbmsService.executionQueue().flush();
  }

  private void createAndSaveRandomProcessInstances() {
    for (int i = 0; i < 20; i++) {
      rdbmsService.getProcessInstanceRdbmsService().create(ProcessInstanceFixtures.createRandomized(b -> b));
    }

    rdbmsService.executionQueue().flush();
  }

  private void createAndSaveProcessInstance(ProcessInstanceDbModel processInstance) {
    createAndSaveProcessInstances(List.of(processInstance));
  }

  private void createAndSaveProcessInstances(List<ProcessInstanceDbModel> processInstanceList) {
    for (ProcessInstanceDbModel processInstance : processInstanceList) {
      rdbmsService.getProcessInstanceRdbmsService().create(processInstance);
    }
    rdbmsService.executionQueue().flush();
  }
}
