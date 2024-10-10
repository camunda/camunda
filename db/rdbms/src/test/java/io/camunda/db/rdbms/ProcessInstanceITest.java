/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static io.camunda.db.rdbms.fixtures.ProcessInstanceFixtures.createAndSaveProcessInstance;
import static io.camunda.db.rdbms.fixtures.ProcessInstanceFixtures.createAndSaveRandomProcessInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.fixtures.ProcessInstanceFixtures;
import io.camunda.db.rdbms.read.domain.ProcessInstanceDbFilter;
import io.camunda.db.rdbms.read.service.ProcessInstanceReader;
import io.camunda.db.rdbms.util.CamundaDatabaseTestApplication;
import io.camunda.db.rdbms.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessInstanceSort;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessInstanceITest {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindProcessInstanceByKey(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveProcessInstance(
        rdbmsWriter,
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processInstanceKey(42L)
                    .bpmnProcessId("test-process")
                    .processDefinitionKey(1337L)
                    .state(ProcessInstanceState.ACTIVE)
                    .startDate(NOW)
                    .parentProcessInstanceKey(-1L)
                    .parentElementInstanceKey(-1L)
                    .version(1)));

    final var instance = processInstanceReader.findOne(42L);

    assertThat(instance).isNotNull();
    assertThat(instance.key()).isEqualTo(42L);
    assertThat(instance.bpmnProcessId()).isEqualTo("test-process");
    assertThat(instance.processDefinitionKey()).isEqualTo(1337L);
    assertThat(instance.state()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(OffsetDateTime.parse(instance.startDate()))
        .isCloseTo(NOW, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.parentProcessInstanceKey()).isEqualTo(-1L);
    assertThat(instance.parentFlowNodeInstanceKey()).isEqualTo(-1L);
    assertThat(instance.processVersion()).isEqualTo(1);
  }

  @TestTemplate
  public void shouldFindProcessInstanceByBpmnProcessId(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveProcessInstance(
        rdbmsWriter,
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processInstanceKey(42L)
                    .bpmnProcessId("test-process")
                    .processDefinitionKey(1337L)
                    .state(ProcessInstanceState.ACTIVE)
                    .startDate(NOW)
                    .parentProcessInstanceKey(-1L)
                    .parentElementInstanceKey(-1L)
                    .version(1)));

    final var searchResult =
        processInstanceReader.search(
            new ProcessInstanceDbFilter(
                new ProcessInstanceFilter.Builder().processDefinitionIds("test-process").build(),
                ProcessInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);

    final var instance = searchResult.hits().getFirst();

    assertThat(instance.key()).isEqualTo(42L);
    assertThat(instance.bpmnProcessId()).isEqualTo("test-process");
    assertThat(instance.processDefinitionKey()).isEqualTo(1337L);
    assertThat(instance.state()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(OffsetDateTime.parse(instance.startDate()))
        .isCloseTo(NOW, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.parentProcessInstanceKey()).isEqualTo(-1L);
    assertThat(instance.parentFlowNodeInstanceKey()).isEqualTo(-1L);
    assertThat(instance.processVersion()).isEqualTo(1);
  }

  @TestTemplate
  public void shouldFindAllProcessInstancePaged(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveRandomProcessInstances(rdbmsWriter);

    final var searchResult =
        processInstanceReader.search(
            new ProcessInstanceDbFilter(
                new ProcessInstanceFilter.Builder().build(),
                ProcessInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllProcessInstancePageIsNull(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveRandomProcessInstances(rdbmsWriter);

    final var searchResult =
        processInstanceReader.search(
            new ProcessInstanceDbFilter(
                new ProcessInstanceFilter.Builder().build(), ProcessInstanceSort.of(b -> b), null));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(20);
  }

  @TestTemplate
  public void shouldFindAllProcessInstancePageValuesAreNull(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveRandomProcessInstances(rdbmsWriter);

    final var searchResult =
        processInstanceReader.search(
            new ProcessInstanceDbFilter(
                new ProcessInstanceFilter.Builder().build(),
                ProcessInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(20);
  }

  @TestTemplate
  public void shouldFindProcessInstanceWithFullFilter(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

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

    final var searchResult =
        processInstanceReader.search(
            new ProcessInstanceDbFilter(
                new ProcessInstanceFilter.Builder()
                    .processInstanceKeys(42L)
                    .processDefinitionIds("test-process")
                    .processDefinitionKeys(1337L)
                    .states(ProcessInstanceState.ACTIVE.name())
                    .parentProcessInstanceKeys(-1L)
                    .parentFlowNodeInstanceKeys(-1L)
                    .build(),
                ProcessInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(42L);
  }

}
