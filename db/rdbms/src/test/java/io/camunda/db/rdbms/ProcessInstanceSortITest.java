/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static io.camunda.db.rdbms.fixtures.ProcessInstanceFixtures.createAndSaveProcessDefinition;
import static io.camunda.db.rdbms.fixtures.ProcessInstanceFixtures.createAndSaveProcessInstances;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.db.rdbms.fixtures.ProcessDefinitionFixtures;
import io.camunda.db.rdbms.fixtures.ProcessInstanceFixtures;
import io.camunda.db.rdbms.read.domain.ProcessInstanceDbFilter;
import io.camunda.db.rdbms.read.service.ProcessInstanceReader;
import io.camunda.db.rdbms.util.CamundaDatabaseTestApplication;
import io.camunda.db.rdbms.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.BadSqlGrammarException;

@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessInstanceSortITest {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSortAllProcessInstancesByBpmnProcessId(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveProcessInstances(rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).bpmnProcessId("test-process-2")),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).bpmnProcessId("test-process-1")),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).bpmnProcessId("test-process-4")),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).bpmnProcessId("test-process-3"))));

    final var searchResult = processInstanceReader
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.processDefinitionId().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @TestTemplate
  public void shouldSortAllProcessInstancesByProcessVersion(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveProcessInstances(rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(b -> b.processInstanceKey(1L).version(2)),
            ProcessInstanceFixtures.createRandomized(b -> b.processInstanceKey(2L).version(1)),
            ProcessInstanceFixtures.createRandomized(b -> b.processInstanceKey(3L).version(4)),
            ProcessInstanceFixtures.createRandomized(b -> b.processInstanceKey(4L).version(3))));

    final var searchResult = processInstanceReader.search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.processDefinitionVersion().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @TestTemplate
  public void shouldSortAllProcessInstancesByProcessDefinitionKey(
      final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveProcessInstances(rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).processDefinitionKey(2L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).processDefinitionKey(1L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).processDefinitionKey(4L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).processDefinitionKey(3L))));

    final var searchResult = processInstanceReader.search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.processDefinitionKey().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @TestTemplate
  public void shouldSortAllProcessInstancesByProcessName(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(1L).name("Test Process 1")));
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(2L).name("Test Process 2")));
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(3L).name("Test Process 3")));
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(4L).name("Test Process 4")));
    createAndSaveProcessInstances(rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).processDefinitionKey(2L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).processDefinitionKey(1L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).processDefinitionKey(4L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).processDefinitionKey(3L))));

    final var searchResult = processInstanceReader.search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.processDefinitionName().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @TestTemplate
  public void shouldSortAllProcessInstancesByProcessVersionTag(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(1L).versionTag("Version 1")));
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(2L).versionTag("Version 2")));
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(3L).versionTag("Version 3")));
    createAndSaveProcessDefinition(
        rdbmsWriter,
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(4L).versionTag("Version 4")));
    createAndSaveProcessInstances(rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).processDefinitionKey(2L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).processDefinitionKey(1L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).processDefinitionKey(4L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).processDefinitionKey(3L))));

    final var searchResult =processInstanceReader.search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.processDefinitionVersionTag().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @TestTemplate
  public void shouldSortAllProcessInstanceByStartDate(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveProcessInstances(rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).startDate(NOW.plusDays(2))),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).startDate(NOW.plusDays(1))),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).startDate(NOW.plusDays(4))),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).startDate(NOW.plusDays(3)))));

    final var searchResult = processInstanceReader.search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.startDate().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @TestTemplate
  public void shouldSortAllProcessInstanceByEndDate(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveProcessInstances(rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).endDate(NOW.plusDays(2))),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).endDate(NOW.plusDays(1))),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).endDate(NOW.plusDays(4))),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).endDate(NOW.plusDays(3)))));

    final var searchResult = processInstanceReader.search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.endDate().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @TestTemplate
  public void shouldSortAllProcessInstanceByParentProcessInstanceKey(
      final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveProcessInstances(rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).parentProcessInstanceKey(2L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).parentProcessInstanceKey(1L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).parentProcessInstanceKey(4L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).parentProcessInstanceKey(3L))));

    final var searchResult = processInstanceReader.search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.parentProcessInstanceKey().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @TestTemplate
  public void shouldSortAllProcessInstanceByTenantId(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveProcessInstances(rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).tenantId("tenant-2")),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).tenantId("tenant-1")),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).tenantId("tenant-4")),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).tenantId("tenant-3"))));

    final var searchResult = processInstanceReader.search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.tenantId().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @TestTemplate
  public void shouldSortAllProcessInstanceByBpmnProcessIdAndStartDate(
      final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    createAndSaveProcessInstances(rdbmsWriter,
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processInstanceKey(1L)
                        .bpmnProcessId("test-process-2")
                        .startDate(NOW.plusDays(1))),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processInstanceKey(2L)
                        .bpmnProcessId("test-process-1")
                        .startDate(NOW.plusDays(1))),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processInstanceKey(3L)
                        .bpmnProcessId("test-process-1")
                        .startDate(NOW.plusDays(2))),
            ProcessInstanceFixtures.createRandomized(
                b ->
                    b.processInstanceKey(4L)
                        .bpmnProcessId("test-process-2")
                        .startDate(NOW.plusDays(2)))));

    final var searchResult = processInstanceReader.search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.processDefinitionId().asc().startDate().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 3L, 1L, 4L);
  }

  @TestTemplate
  public void shouldFailOnUnknownSortingOption(final CamundaDatabaseTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final ProcessInstanceReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    assertThatThrownBy(
            () -> processInstanceReader.search(
                        new ProcessInstanceDbFilter(
                            new ProcessInstanceFilter.Builder().build(),
                            new ProcessInstanceSort(
                                List.of(new FieldSorting("foo", SortOrder.ASC))),
                            SearchQueryPage.of(b -> b)))
                    .hits())
        .isInstanceOf(BadSqlGrammarException.class);
  }

}
