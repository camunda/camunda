/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.flownodeinstance;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.sort.FlowNodeInstanceSort;
import io.camunda.search.sort.FlowNodeInstanceSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class FlowNodeInstanceSortIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSortByFlowNodeInstanceKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.flowNodeInstanceKey().asc(),
        Comparator.comparing(FlowNodeInstanceEntity::flowNodeInstanceKey));
  }

  @TestTemplate
  public void shouldSortByFlowNodeInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.flowNodeInstanceKey().desc(),
        Comparator.comparing(FlowNodeInstanceEntity::flowNodeInstanceKey).reversed());
  }

  @TestTemplate
  public void shouldSortByFlowNodeIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.flowNodeId().asc(),
        Comparator.comparing(FlowNodeInstanceEntity::flowNodeId));
  }

  @TestTemplate
  public void shouldSortByFlowNodeIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.flowNodeId().desc(),
        Comparator.comparing(FlowNodeInstanceEntity::flowNodeId).reversed());
  }

  @TestTemplate
  public void shouldSortByProcessInstanceKey(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processInstanceKey().asc(),
        Comparator.comparing(FlowNodeInstanceEntity::processInstanceKey));
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionKey(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processDefinitionKey().asc(),
        Comparator.comparing(FlowNodeInstanceEntity::processDefinitionKey));
  }

  @TestTemplate
  public void shouldSortByStartDateAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.startDate().asc(),
        Comparator.comparing(FlowNodeInstanceEntity::startDate));
  }

  @TestTemplate
  public void shouldSortByStartDateDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.startDate().desc(),
        Comparator.comparing(FlowNodeInstanceEntity::startDate).reversed());
  }

  @TestTemplate
  public void shouldSortByEndDate(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.endDate().asc(),
        Comparator.comparing(FlowNodeInstanceEntity::endDate));
  }

  @TestTemplate
  public void shouldSortByState(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.state().asc(),
        Comparator.comparing(FlowNodeInstanceEntity::state));
  }

  @TestTemplate
  public void shouldSortByTenantId(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().asc(),
        Comparator.comparing(FlowNodeInstanceEntity::tenantId));
  }

  @TestTemplate
  public void shouldSortByIncidentKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.incidentKey().asc(),
        Comparator.nullsLast(
            Comparator.comparing(
                FlowNodeInstanceEntity::incidentKey,
                Comparator.nullsLast(Comparator.naturalOrder()))));
  }

  @TestTemplate
  public void shouldSortByIncidentKeyDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.incidentKey().desc(),
        Comparator.nullsLast(
            Comparator.comparing(
                    FlowNodeInstanceEntity::incidentKey,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()));
  }

  @TestTemplate
  public void shouldSortByType(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.type().asc(),
        Comparator.comparing(o -> o.type().name()));
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<FlowNodeInstanceSort>> sortBuilder,
      final Comparator<FlowNodeInstanceEntity> comparator) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var processDefinitionKey = nextKey();
    createAndSaveRandomFlowNodeInstances(
        rdbmsWriter, b -> b.processDefinitionKey(processDefinitionKey));

    final var searchResult =
        reader
            .search(
                new FlowNodeInstanceQuery(
                    new FlowNodeInstanceFilter.Builder()
                        .processDefinitionKeys(processDefinitionKey)
                        .build(),
                    FlowNodeInstanceSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
