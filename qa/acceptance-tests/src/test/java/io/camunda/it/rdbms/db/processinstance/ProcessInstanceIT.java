/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processinstance;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveProcessInstance;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveRandomProcessInstance;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveRandomProcessInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.ProcessInstanceReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.query.ProcessInstanceQuery;
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

  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindProcessInstanceByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processInstanceKey = nextKey();
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
    assertThat(instance.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(instance.processDefinitionId()).isEqualTo("test-process");
    assertThat(instance.processDefinitionKey()).isEqualTo(1337L);
    assertThat(instance.state()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(instance.startDate())
        .isCloseTo(NOW, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.parentProcessInstanceKey()).isEqualTo(-1L);
    assertThat(instance.parentFlowNodeInstanceKey()).isEqualTo(-1L);
    assertThat(instance.processDefinitionVersion()).isEqualTo(1);
    assertThat(instance.hasIncident()).isFalse();
  }

  @TestTemplate
  public void shouldSaveLogAndResolveIncident(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final ProcessInstanceDbModel original = ProcessInstanceFixtures.createRandomized(b -> b);
    createAndSaveProcessInstance(rdbmsWriter, original);
    rdbmsWriter.getProcessInstanceWriter().createIncident(original.processInstanceKey());
    rdbmsWriter.flush();

    final var instance = processInstanceReader.findOne(original.processInstanceKey()).orElse(null);

    assertThat(instance).isNotNull();
    assertThat(instance.hasIncident()).isTrue();

    rdbmsWriter.getProcessInstanceWriter().resolveIncident(original.processInstanceKey());
    rdbmsWriter.flush();

    final var resolvedInstance =
        processInstanceReader.findOne(original.processInstanceKey()).orElse(null);

    assertThat(resolvedInstance).isNotNull();
    assertThat(resolvedInstance.hasIncident()).isFalse();
  }

  @TestTemplate
  public void shouldFindProcessInstanceByBpmnProcessId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processInstanceKey = nextKey();
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
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds("test-process-unique"))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(instance.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(instance.processDefinitionId()).isEqualTo("test-process-unique");
    assertThat(instance.processDefinitionKey()).isEqualTo(1338L);
    assertThat(instance.state()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(instance.startDate())
        .isCloseTo(NOW, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.parentProcessInstanceKey()).isEqualTo(-1L);
    assertThat(instance.parentFlowNodeInstanceKey()).isEqualTo(-1L);
    assertThat(instance.processDefinitionVersion()).isEqualTo(1);
  }

  @TestTemplate
  public void shouldFindProcessInstanceWithIncidents(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processDefinitionKey = nextKey();
    createAndSaveRandomProcessInstances(
        rdbmsWriter, b -> b.processDefinitionKey(processDefinitionKey));
    final var incidentPI1 =
        createAndSaveRandomProcessInstance(
            rdbmsWriter, b -> b.processDefinitionKey(processDefinitionKey).numIncidents(1));
    final var incidentPI2 =
        createAndSaveRandomProcessInstance(
            rdbmsWriter, b -> b.processDefinitionKey(processDefinitionKey).numIncidents(2));

    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinitionKey).hasIncident(true))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);

    assertThat(searchResult.items().stream().map(ProcessInstanceEntity::processInstanceKey))
        .containsExactlyInAnyOrder(
            incidentPI1.processInstanceKey(), incidentPI2.processInstanceKey());
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
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinitionId))
                        .sort(s -> s.startDate().asc().processDefinitionName().asc())
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);

    final var firstInstance = searchResult.items().getFirst();
    assertThat(searchResult.firstSortValues()).hasSize(3);
    assertThat(searchResult.firstSortValues())
        .containsExactly(
            firstInstance.startDate(),
            firstInstance.processDefinitionName(),
            firstInstance.processInstanceKey());
    final var lastInstance = searchResult.items().getLast();
    assertThat(searchResult.lastSortValues()).hasSize(3);
    assertThat(searchResult.lastSortValues())
        .containsExactly(
            lastInstance.startDate(),
            lastInstance.processDefinitionName(),
            lastInstance.processInstanceKey());
  }

  @TestTemplate
  public void shouldFindProcessInstanceWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processInstanceKey = nextKey();
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
                    .version(1)
                    .partitionId(123456)));

    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.processInstanceKeys(processInstanceKey)
                                    .processDefinitionIds("test-process")
                                    .processDefinitionKeys(1337L)
                                    .states(ProcessInstanceState.ACTIVE.name())
                                    .parentProcessInstanceKeys(-1L)
                                    .parentFlowNodeInstanceKeys(-1L)
                                    .partitionId(123456))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().processInstanceKey()).isEqualTo(processInstanceKey);
  }

  @TestTemplate
  public void shouldFindProcessInstanceWithSearchAfter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final var processDefinition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriter, b -> b);
    createAndSaveRandomProcessInstances(
        rdbmsWriter,
        b ->
            b.processDefinitionKey(processDefinition.processDefinitionKey())
                .processDefinitionId(processDefinition.processDefinitionId()));
    final var sort =
        ProcessInstanceSort.of(
            s ->
                s.processDefinitionName()
                    .asc()
                    .processDefinitionVersion()
                    .asc()
                    .startDate()
                    .desc());
    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinition.processDefinitionId()))
                        .sort(sort)
                        .page(p -> p.from(0).size(20))));

    final var instanceAfter = searchResult.items().get(9);
    final var nextPage =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinition.processDefinitionId()))
                        .sort(sort)
                        .page(
                            p ->
                                p.size(5)
                                    .searchAfter(
                                        new Object[] {
                                          instanceAfter.processDefinitionName(),
                                          instanceAfter.processDefinitionVersion(),
                                          instanceAfter.startDate(),
                                          instanceAfter.processInstanceKey()
                                        }))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(10, 15));
  }

  @TestTemplate
  public void shouldCleanup(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final var cleanupDate = NOW.minusDays(1);

    final var processDefinition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriter, b -> b);
    final var pi1 =
        createAndSaveRandomProcessInstance(
            rdbmsWriter,
            b ->
                b.processDefinitionKey(processDefinition.processDefinitionKey())
                    .partitionId(PARTITION_ID));
    final var pi2 =
        createAndSaveRandomProcessInstance(
            rdbmsWriter,
            b ->
                b.processDefinitionKey(processDefinition.processDefinitionKey())
                    .partitionId(PARTITION_ID));
    final var pi3 =
        createAndSaveRandomProcessInstance(
            rdbmsWriter,
            b ->
                b.processDefinitionKey(processDefinition.processDefinitionKey())
                    .partitionId(PARTITION_ID));

    // set cleanup dates
    rdbmsWriter.getProcessInstanceWriter().scheduleForHistoryCleanup(pi1.processInstanceKey(), NOW);
    rdbmsWriter
        .getProcessInstanceWriter()
        .scheduleForHistoryCleanup(pi2.processInstanceKey(), NOW.minusDays(2));
    rdbmsWriter.flush();

    // cleanup
    rdbmsWriter.getProcessInstanceWriter().cleanupHistory(PARTITION_ID, cleanupDate, 10);

    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinition.processDefinitionKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(ProcessInstanceEntity::processInstanceKey))
        .containsExactlyInAnyOrder(pi1.processInstanceKey(), pi3.processInstanceKey());
  }
}
