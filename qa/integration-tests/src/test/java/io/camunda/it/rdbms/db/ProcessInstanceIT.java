/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveProcessInstance;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveRandomProcessInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.domain.ProcessInstanceDbQuery;
import io.camunda.db.rdbms.read.service.ProcessInstanceReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessInstanceSort;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessInstanceIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindProcessInstanceByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processInstanceKey = ProcessInstanceFixtures.nextKey();
    createAndSaveProcessInstance(
        rdbmsWriter,
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processInstanceKey(processInstanceKey)
                    .processDefinitionId("test-process")
                    .processDefinitionKey(1337L)
                    .state(ProcessInstanceState.ACTIVE)
                    .startDate(NOW)
                    .parentProcessInstanceKey(-1L)
                    .parentElementInstanceKey(-1L)
                    .version(1)));

    final var instance = processInstanceReader.findOne(processInstanceKey).orElse(null);

    assertThat(instance).isNotNull();
    assertThat(instance.key()).isEqualTo(processInstanceKey);
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
  public void shouldFindProcessInstanceByBpmnProcessId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processInstanceKey = ProcessInstanceFixtures.nextKey();
    createAndSaveProcessInstance(
        rdbmsWriter,
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processInstanceKey(processInstanceKey)
                    .processDefinitionId("test-process-unique")
                    .processDefinitionKey(1338L)
                    .state(ProcessInstanceState.ACTIVE)
                    .startDate(NOW)
                    .parentProcessInstanceKey(-1L)
                    .parentElementInstanceKey(-1L)
                    .version(1)));

    final var searchResult =
        processInstanceReader.search(
            new ProcessInstanceDbQuery(
                new ProcessInstanceFilter.Builder()
                    .processDefinitionIds("test-process-unique")
                    .build(),
                ProcessInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);

    final var instance = searchResult.hits().getFirst();

    assertThat(instance.key()).isEqualTo(processInstanceKey);
    assertThat(instance.bpmnProcessId()).isEqualTo("test-process-unique");
    assertThat(instance.processDefinitionKey()).isEqualTo(1338L);
    assertThat(instance.state()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(OffsetDateTime.parse(instance.startDate()))
        .isCloseTo(NOW, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.parentProcessInstanceKey()).isEqualTo(-1L);
    assertThat(instance.parentFlowNodeInstanceKey()).isEqualTo(-1L);
    assertThat(instance.processVersion()).isEqualTo(1);
  }

  @TestTemplate
  public void shouldFindAllProcessInstancePaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final String processDefinitionId = ProcessInstanceFixtures.nextStringId();
    createAndSaveRandomProcessInstances(
        rdbmsWriter, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        processInstanceReader.search(
            new ProcessInstanceDbQuery(
                new ProcessInstanceFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                ProcessInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllProcessInstancePageValuesAreNull(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final String processDefinitionId = ProcessInstanceFixtures.nextStringId();
    createAndSaveRandomProcessInstances(
        rdbmsWriter, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        processInstanceReader.search(
            new ProcessInstanceDbQuery(
                new ProcessInstanceFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                ProcessInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isGreaterThanOrEqualTo(20);
    assertThat(searchResult.hits()).hasSizeGreaterThanOrEqualTo(20);
  }

  @TestTemplate
  public void shouldFindProcessInstanceWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processInstanceKey = ProcessInstanceFixtures.nextKey();
    createAndSaveRandomProcessInstances(rdbmsWriter);
    createAndSaveProcessInstance(
        rdbmsWriter,
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processInstanceKey(processInstanceKey)
                    .processDefinitionId("test-process")
                    .processDefinitionKey(1337L)
                    .state(ProcessInstanceState.ACTIVE)
                    .startDate(NOW)
                    .endDate(NOW)
                    .parentProcessInstanceKey(-1L)
                    .parentElementInstanceKey(-1L)
                    .version(1)));

    final var searchResult =
        processInstanceReader.search(
            new ProcessInstanceDbQuery(
                new ProcessInstanceFilter.Builder()
                    .processInstanceKeys(processInstanceKey)
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
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(processInstanceKey);
  }
}
