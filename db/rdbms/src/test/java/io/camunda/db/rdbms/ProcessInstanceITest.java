/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.db.rdbms.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.domain.ProcessInstanceDbFilter;
import io.camunda.db.rdbms.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.fixtures.ProcessDefinitionFixtures;
import io.camunda.db.rdbms.fixtures.ProcessInstanceFixtures;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJdbcTest
@ContextConfiguration(classes = {TestConfiguration.class, RdbmsConfiguration.class})
@AutoConfigurationPackage
@ActiveProfiles("test-h2")
public class ProcessInstanceITest {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired private RdbmsService rdbmsService;

  @Test
  public void shouldSaveAndFindProcessInstanceByKey() {
    createAndSaveProcessInstance(
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

    final var instance = rdbmsService.getProcessInstanceRdbmsService().findOne(42L);

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

  @Test
  public void shouldFindProcessInstanceByBpmnProcessId() {
    createAndSaveProcessInstance(
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
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder()
                        .processDefinitionIds("test-process")
                        .build(),
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

  @Test
  public void shouldFindAllProcessInstancePaged() {
    createAndSaveRandomProcessInstances();

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(5);
  }

  @Test
  public void shouldFindAllProcessInstancePageIsNull() {
    createAndSaveRandomProcessInstances();

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b),
                    null));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(20);
  }

  @Test
  public void shouldFindAllProcessInstancePageValuesAreNull() {
    createAndSaveRandomProcessInstances();

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(20);
  }

  @Test
  public void shouldFindProcessInstanceWithFullFilter() {
    createAndSaveRandomProcessInstances();
    createAndSaveProcessInstance(
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
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
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

  @ParameterizedTest
  @MethodSource("shouldFindProcessInstanceWithSpecificFilterParameters")
  public void shouldFindProcessInstanceWithSpecificFilter(final ProcessInstanceFilter filter) {
    createAndSaveProcessDefinition(
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(1337L)
                    .bpmnProcessId("test-process")
                    .name("Test Process")
                    .versionTag("Version 1")));
    createAndSaveRandomProcessInstances();
    createAndSaveProcessInstance(
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
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    filter,
                    ProcessInstanceSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(42L);
  }

  @Test
  public void shouldSortAllProcessInstancesByBpmnProcessId() {
    createAndSaveProcessInstances(
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).bpmnProcessId("test-process-2")),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).bpmnProcessId("test-process-1")),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).bpmnProcessId("test-process-4")),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).bpmnProcessId("test-process-3"))));

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
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

  @Test
  public void shouldSortAllProcessInstancesByProcessVersion() {
    createAndSaveProcessInstances(
        List.of(
            ProcessInstanceFixtures.createRandomized(b -> b.processInstanceKey(1L).version(2)),
            ProcessInstanceFixtures.createRandomized(b -> b.processInstanceKey(2L).version(1)),
            ProcessInstanceFixtures.createRandomized(b -> b.processInstanceKey(3L).version(4)),
            ProcessInstanceFixtures.createRandomized(b -> b.processInstanceKey(4L).version(3))));

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.processDefinitionVersion().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @Test
  public void shouldSortAllProcessInstancesByProcessDefinitionKey() {
    createAndSaveProcessInstances(
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).processDefinitionKey(2L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).processDefinitionKey(1L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).processDefinitionKey(4L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).processDefinitionKey(3L))));

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.processDefinitionKey().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @Test
  public void shouldSortAllProcessInstancesByProcessName() {
    createAndSaveProcessDefinition(
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(1L).name("Test Process 1")));
    createAndSaveProcessDefinition(
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(2L).name("Test Process 2")));
    createAndSaveProcessDefinition(
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(3L).name("Test Process 3")));
    createAndSaveProcessDefinition(
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(4L).name("Test Process 4")));
    createAndSaveProcessInstances(
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).processDefinitionKey(2L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).processDefinitionKey(1L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).processDefinitionKey(4L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).processDefinitionKey(3L))));

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.processDefinitionName().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @Test
  public void shouldSortAllProcessInstancesByProcessVersionTag() {
    createAndSaveProcessDefinition(
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(1L).versionTag("Version 1")));
    createAndSaveProcessDefinition(
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(2L).versionTag("Version 2")));
    createAndSaveProcessDefinition(
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(3L).versionTag("Version 3")));
    createAndSaveProcessDefinition(
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionKey(4L).versionTag("Version 4")));
    createAndSaveProcessInstances(
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).processDefinitionKey(2L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).processDefinitionKey(1L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).processDefinitionKey(4L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).processDefinitionKey(3L))));

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.processDefinitionVersionTag().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @Test
  public void shouldSortAllProcessInstanceByStartDate() {
    createAndSaveProcessInstances(
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).startDate(NOW.plusDays(2))),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).startDate(NOW.plusDays(1))),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).startDate(NOW.plusDays(4))),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).startDate(NOW.plusDays(3)))));

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.startDate().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @Test
  public void shouldSortAllProcessInstanceByEndDate() {
    createAndSaveProcessInstances(
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).endDate(NOW.plusDays(2))),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).endDate(NOW.plusDays(1))),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).endDate(NOW.plusDays(4))),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).endDate(NOW.plusDays(3)))));

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.endDate().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @Test
  public void shouldSortAllProcessInstanceByParentProcessInstanceKey() {
    createAndSaveProcessInstances(
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).parentProcessInstanceKey(2L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).parentProcessInstanceKey(1L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).parentProcessInstanceKey(4L)),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).parentProcessInstanceKey(3L))));

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.parentProcessInstanceKey().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @Test
  public void shouldSortAllProcessInstanceByTenantId() {
    createAndSaveProcessInstances(
        List.of(
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(1L).tenantId("tenant-2")),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(2L).tenantId("tenant-1")),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(3L).tenantId("tenant-4")),
            ProcessInstanceFixtures.createRandomized(
                b -> b.processInstanceKey(4L).tenantId("tenant-3"))));

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.tenantId().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 1L, 4L, 3L);
  }

  @Test
  public void shouldSortAllProcessInstanceByBpmnProcessIdAndStartDate() {
    createAndSaveProcessInstances(
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

    final var searchResult =
        rdbmsService
            .getProcessInstanceRdbmsService()
            .search(
                new ProcessInstanceDbFilter(
                    new ProcessInstanceFilter.Builder().build(),
                    ProcessInstanceSort.of(b -> b.processDefinitionId().asc().startDate().asc()),
                    SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessInstanceEntity::key).toList())
        .containsExactly(2L, 3L, 1L, 4L);
  }

  @Test
  public void shouldFailOnUnknownSortingOption() {
    assertThatThrownBy(
            () ->
                rdbmsService
                    .getProcessInstanceRdbmsService()
                    .search(
                        new ProcessInstanceDbFilter(
                            new ProcessInstanceFilter.Builder().build(),
                            new ProcessInstanceSort(
                                List.of(new FieldSorting("foo", SortOrder.ASC))),
                            SearchQueryPage.of(b -> b)))
                    .hits())
        .isInstanceOf(BadSqlGrammarException.class);
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

  private void createAndSaveProcessDefinition(final ProcessDefinitionDbModel processDefinition) {
    rdbmsService.getProcessDefinitionRdbmsService().save(processDefinition);
    rdbmsService.executionQueue().flush();
  }

  private void createAndSaveRandomProcessInstances() {
    for (int i = 0; i < 20; i++) {
      rdbmsService
          .getProcessInstanceRdbmsService()
          .create(ProcessInstanceFixtures.createRandomized(b -> b));
    }

    rdbmsService.executionQueue().flush();
  }

  private void createAndSaveProcessInstance(final ProcessInstanceDbModel processInstance) {
    createAndSaveProcessInstances(List.of(processInstance));
  }

  private void createAndSaveProcessInstances(
      final List<ProcessInstanceDbModel> processInstanceList) {
    for (final ProcessInstanceDbModel processInstance : processInstanceList) {
      rdbmsService.getProcessInstanceRdbmsService().create(processInstance);
    }
    rdbmsService.executionQueue().flush();
  }
}
