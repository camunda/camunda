/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processinstance;

import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveRandomProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.ProcessInstanceReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.result.ProcessInstanceQueryResultConfig;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.ProcessInstanceSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.Random;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessInstanceSortIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSortByProcessInstanceKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processInstanceKey().asc(),
        Comparator.comparing(ProcessInstanceEntity::processInstanceKey));
  }

  @TestTemplate
  public void shouldSortByProcessInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processInstanceKey().desc(),
        Comparator.comparing(ProcessInstanceEntity::processInstanceKey).reversed());
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processDefinitionKey().asc(),
        Comparator.comparing(ProcessInstanceEntity::processDefinitionKey));
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionIdAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processDefinitionId().asc(),
        Comparator.comparing(ProcessInstanceEntity::processDefinitionId));
  }

  @TestTemplate
  public void shouldSortByStartDateAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.startDate().asc(),
        Comparator.comparing(ProcessInstanceEntity::startDate));
  }

  @TestTemplate
  public void shouldSortByEndDateAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.endDate().asc(),
        Comparator.comparing(ProcessInstanceEntity::endDate));
  }

  @TestTemplate
  public void shouldSortByTenantIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().asc(),
        Comparator.comparing(ProcessInstanceEntity::tenantId));
  }

  @TestTemplate
  public void shouldSortByHasIncidentAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.hasIncident().asc(),
        Comparator.comparing(ProcessInstanceEntity::hasIncident));
  }

  @TestTemplate
  public void shouldSortByParentProcessInstanceKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.parentProcessInstanceKey().asc(),
        Comparator.comparing(ProcessInstanceEntity::parentProcessInstanceKey));
  }

  @TestTemplate
  public void shouldSortByParentElementInstanceKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.parentFlowNodeInstanceKey().asc(),
        Comparator.comparing(ProcessInstanceEntity::parentFlowNodeInstanceKey));
  }

  @TestTemplate
  public void shouldSortByStateAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.state().asc(),
        Comparator.comparing(p -> p.state().name()));
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<ProcessInstanceSort>> sortBuilder,
      final Comparator<ProcessInstanceEntity> comparator) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceReader reader = rdbmsService.getProcessInstanceReader();

    final var version = new Random().nextInt(32767);
    for (int i = 0; i < 20; i++) {
      final var def =
          ProcessDefinitionFixtures.createAndSaveProcessDefinition(
              rdbmsWriter, b -> b.version(version));

      createAndSaveRandomProcessInstance(
          rdbmsWriter,
          b ->
              b.processDefinitionKey(def.processDefinitionKey())
                  .processDefinitionId(def.processDefinitionId())
                  .version(def.version()));
    }

    final var searchResult =
        reader
            .search(
                new ProcessInstanceQuery(
                    new ProcessInstanceFilter.Builder().processDefinitionVersions(version).build(),
                    ProcessInstanceSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b),
                    ProcessInstanceQueryResultConfig.of(b -> b)))
            .items();

    assertThat(searchResult.size()).isGreaterThanOrEqualTo(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
