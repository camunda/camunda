/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processinstance;

import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveProcessDefinition;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveProcessInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.ProcessInstanceReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessInstanceSortIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSortAllProcessInstancesByBpmnProcessId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processDefinitionKey = ProcessInstanceFixtures.nextKey(); // For Test Scope
    final Long processInstanceKey = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey2 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey3 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey4 = ProcessInstanceFixtures.nextKey();
    createAndSaveProcessInstances(
        rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionKey(processDefinitionKey)
                        .processInstanceKey(processInstanceKey)
                        .processDefinitionId("test-process-2")),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionKey(processDefinitionKey)
                        .processInstanceKey(processInstanceKey2)
                        .processDefinitionId("test-process-1")),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionKey(processDefinitionKey)
                        .processInstanceKey(processInstanceKey3)
                        .processDefinitionId("test-process-4")),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionKey(processDefinitionKey)
                        .processInstanceKey(processInstanceKey4)
                        .processDefinitionId("test-process-3"))));

    final var searchResult =
        processInstanceReader
            .search(
                ProcessInstanceQuery.of(
                    b ->
                        b.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                            .sort(s -> s.processDefinitionId().asc())
                            .page(p -> p.from(0).size(5))))
            .items();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(
            processInstanceKey2, processInstanceKey, processInstanceKey4, processInstanceKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessInstancesByProcessVersion(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final String bpmProcessId = ProcessInstanceFixtures.nextStringId();
    final Long processInstanceKey = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey2 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey3 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey4 = ProcessInstanceFixtures.nextKey();
    createAndSaveProcessInstances(
        rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey)
                        .version(2)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey2)
                        .version(1)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey3)
                        .version(4)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey4)
                        .version(3))));

    final var searchResult =
        processInstanceReader
            .search(
                ProcessInstanceQuery.of(
                    b ->
                        b.filter(f -> f.processDefinitionIds(bpmProcessId))
                            .sort(s -> s.processDefinitionVersion().asc())
                            .page(p -> p.from(0).size(5))))
            .items();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(
            processInstanceKey2, processInstanceKey, processInstanceKey4, processInstanceKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessInstancesByProcessDefinitionKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final String bpmProcessId = ProcessInstanceFixtures.nextStringId();
    final Long processInstanceKey = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey2 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey3 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey4 = ProcessInstanceFixtures.nextKey();
    createAndSaveProcessInstances(
        rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey)
                        .processDefinitionKey(processInstanceKey2)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey2)
                        .processDefinitionKey(processInstanceKey)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey3)
                        .processDefinitionKey(processInstanceKey4)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey4)
                        .processDefinitionKey(processInstanceKey3))));

    final var searchResult =
        processInstanceReader
            .search(
                ProcessInstanceQuery.of(
                    b ->
                        b.filter(f -> f.processDefinitionIds(bpmProcessId))
                            .sort(s -> s.processDefinitionKey().asc())
                            .page(p -> p.from(0).size(5))))
            .items();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(
            processInstanceKey2, processInstanceKey, processInstanceKey4, processInstanceKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessInstancesByProcessName(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final String bpmProcessId = ProcessInstanceFixtures.nextStringId();
    final Long processInstanceKey = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey2 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey3 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey4 = ProcessInstanceFixtures.nextKey();
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionId(bpmProcessId)
                    .processDefinitionKey(processInstanceKey)
                    .name("Test Process 1")));
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionId(bpmProcessId)
                    .processDefinitionKey(processInstanceKey2)
                    .name("Test Process 2")));
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionId(bpmProcessId)
                    .processDefinitionKey(processInstanceKey3)
                    .name("Test Process 3")));
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionId(bpmProcessId)
                    .processDefinitionKey(processInstanceKey4)
                    .name("Test Process 4")));
    createAndSaveProcessInstances(
        rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey)
                        .processDefinitionKey(processInstanceKey2)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey2)
                        .processDefinitionKey(processInstanceKey)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey3)
                        .processDefinitionKey(processInstanceKey4)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey4)
                        .processDefinitionKey(processInstanceKey3))));

    final var searchResult =
        processInstanceReader
            .search(
                ProcessInstanceQuery.of(
                    b ->
                        b.filter(f -> f.processDefinitionIds(bpmProcessId))
                            .sort(s -> s.processDefinitionName().asc())
                            .page(p -> p.from(0).size(5))))
            .items();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(
            processInstanceKey2, processInstanceKey, processInstanceKey4, processInstanceKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessInstancesByProcessVersionTag(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final String bpmProcessId = ProcessInstanceFixtures.nextStringId();
    final Long processInstanceKey = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey2 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey3 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey4 = ProcessInstanceFixtures.nextKey();
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionId(bpmProcessId)
                    .processDefinitionKey(processInstanceKey)
                    .versionTag("Version 1")));
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionId(bpmProcessId)
                    .processDefinitionKey(processInstanceKey2)
                    .versionTag("Version 2")));
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionId(bpmProcessId)
                    .processDefinitionKey(processInstanceKey3)
                    .versionTag("Version 3")));
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionId(bpmProcessId)
                    .processDefinitionKey(processInstanceKey4)
                    .versionTag("Version 4")));
    createAndSaveProcessInstances(
        rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey)
                        .processDefinitionKey(processInstanceKey2)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey2)
                        .processDefinitionKey(processInstanceKey)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey3)
                        .processDefinitionKey(processInstanceKey4)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey4)
                        .processDefinitionKey(processInstanceKey3))));

    final var searchResult =
        processInstanceReader
            .search(
                ProcessInstanceQuery.of(
                    b ->
                        b.filter(f -> f.processDefinitionIds(bpmProcessId))
                            .sort(s -> s.processDefinitionVersionTag().asc())
                            .page(p -> p)))
            .items();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(
            processInstanceKey2, processInstanceKey, processInstanceKey4, processInstanceKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessInstanceByStartDate(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final String bpmProcessId = ProcessInstanceFixtures.nextStringId();
    final Long processInstanceKey = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey2 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey3 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey4 = ProcessInstanceFixtures.nextKey();
    createAndSaveProcessInstances(
        rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey)
                        .startDate(NOW.plusDays(2))),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey2)
                        .startDate(NOW.plusDays(1))),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey3)
                        .startDate(NOW.plusDays(4))),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey4)
                        .startDate(NOW.plusDays(3)))));

    final var searchResult =
        processInstanceReader
            .search(
                ProcessInstanceQuery.of(
                    b ->
                        b.filter(f -> f.processDefinitionIds(bpmProcessId))
                            .sort(s -> s.startDate().asc())
                            .page(p -> p)))
            .items();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(
            processInstanceKey2, processInstanceKey, processInstanceKey4, processInstanceKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessInstanceByEndDate(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final String bpmProcessId = ProcessInstanceFixtures.nextStringId();
    final Long processInstanceKey = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey2 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey3 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey4 = ProcessInstanceFixtures.nextKey();
    createAndSaveProcessInstances(
        rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey)
                        .endDate(NOW.plusDays(2))),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey2)
                        .endDate(NOW.plusDays(1))),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey3)
                        .endDate(NOW.plusDays(4))),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey4)
                        .endDate(NOW.plusDays(3)))));

    final var searchResult =
        processInstanceReader
            .search(
                ProcessInstanceQuery.of(
                    b ->
                        b.filter(f -> f.processDefinitionIds(bpmProcessId))
                            .sort(s -> s.endDate().asc())
                            .page(p -> p)))
            .items();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(
            processInstanceKey2, processInstanceKey, processInstanceKey4, processInstanceKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessInstanceByParentProcessInstanceKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final String bpmProcessId = ProcessInstanceFixtures.nextStringId();
    final Long processInstanceKey = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey2 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey3 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey4 = ProcessInstanceFixtures.nextKey();
    createAndSaveProcessInstances(
        rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey)
                        .parentProcessInstanceKey(processInstanceKey2)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey2)
                        .parentProcessInstanceKey(processInstanceKey)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey3)
                        .parentProcessInstanceKey(processInstanceKey4)),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey4)
                        .parentProcessInstanceKey(processInstanceKey3))));

    final var searchResult =
        processInstanceReader
            .search(
                ProcessInstanceQuery.of(
                    b ->
                        b.filter(f -> f.processDefinitionIds(bpmProcessId))
                            .sort(s -> s.parentProcessInstanceKey().asc())
                            .page(p -> p)))
            .items();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(
            processInstanceKey2, processInstanceKey, processInstanceKey4, processInstanceKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessInstanceByTenantId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final String bpmProcessId = ProcessInstanceFixtures.nextStringId();
    final Long processInstanceKey = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey2 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey3 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey4 = ProcessInstanceFixtures.nextKey();
    createAndSaveProcessInstances(
        rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey)
                        .tenantId("tenant-2")),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey2)
                        .tenantId("tenant-1")),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey3)
                        .tenantId("tenant-4")),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionId(bpmProcessId)
                        .processInstanceKey(processInstanceKey4)
                        .tenantId("tenant-3"))));

    final var searchResult =
        processInstanceReader
            .search(
                ProcessInstanceQuery.of(
                    b ->
                        b.filter(f -> f.processDefinitionIds(bpmProcessId))
                            .sort(s -> s.tenantId().asc())
                            .page(p -> p)))
            .items();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(
            processInstanceKey2, processInstanceKey, processInstanceKey4, processInstanceKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessInstanceByBpmnProcessIdAndStartDate(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processDefinitionKey = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey2 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey3 = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey4 = ProcessInstanceFixtures.nextKey();
    createAndSaveProcessInstances(
        rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionKey(processDefinitionKey)
                        .processInstanceKey(processInstanceKey)
                        .processDefinitionId("test-process-2")
                        .startDate(NOW.plusDays(1))),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionKey(processDefinitionKey)
                        .processInstanceKey(processInstanceKey2)
                        .processDefinitionId("test-process-1")
                        .startDate(NOW.plusDays(1))),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionKey(processDefinitionKey)
                        .processInstanceKey(processInstanceKey3)
                        .processDefinitionId("test-process-1")
                        .startDate(NOW.plusDays(2))),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processDefinitionKey(processDefinitionKey)
                        .processInstanceKey(processInstanceKey4)
                        .processDefinitionId("test-process-2")
                        .startDate(NOW.plusDays(2)))));

    final var searchResult =
        processInstanceReader
            .search(
                ProcessInstanceQuery.of(
                    b ->
                        b.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                            .sort(s -> s.processDefinitionId().asc().startDate().asc())
                            .page(p -> p)))
            .items();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(
            processInstanceKey2, processInstanceKey3, processInstanceKey, processInstanceKey4);
  }

  @TestTemplate
  public void shouldNotFailOnUnknownSortingOption(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    processInstanceReader.search(
        ProcessInstanceQuery.of(
            b ->
                b.filter(f -> f)
                    .sort(new ProcessInstanceSort(List.of(new FieldSorting("foo", SortOrder.ASC))))
                    .page(p -> p)));
  }
}
