/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.incident;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.IncidentFixtures.createAndSaveRandomIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.IncidentReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.sort.IncidentSort;
import io.camunda.search.sort.IncidentSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class IncidentSortIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSortByIncidentKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.incidentKey().asc(),
        Comparator.comparing(IncidentEntity::incidentKey));
  }

  @TestTemplate
  public void shouldSortByIncidentKeyDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.incidentKey().desc(),
        Comparator.comparing(IncidentEntity::incidentKey).reversed());
  }

  @TestTemplate
  public void shouldSortByFlowNodeIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.flowNodeId().asc(),
        Comparator.comparing(IncidentEntity::flowNodeId));
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionIdDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processDefinitionId().desc(),
        Comparator.comparing(IncidentEntity::processDefinitionId).reversed());
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processDefinitionKey().asc(),
        Comparator.comparing(IncidentEntity::processDefinitionKey));
  }

  @TestTemplate
  public void shouldSortByProcessInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processInstanceKey().desc(),
        Comparator.comparing(IncidentEntity::processInstanceKey).reversed());
  }

  @TestTemplate
  public void shouldSortByErrorTypeAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.errorType().asc(),
        Comparator.comparing(it -> it.errorType().name()));
  }

  @TestTemplate
  public void shouldSortByErrorMessageAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.errorMessage().asc(),
        Comparator.comparing(IncidentEntity::errorMessage));
  }

  @TestTemplate
  public void shouldSortByStateAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.state().asc(),
        Comparator.comparing(it -> it.state().name()));
  }

  @TestTemplate
  public void shouldSortByJobKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.jobKey().desc(),
        Comparator.comparing(IncidentEntity::jobKey).reversed());
  }

  @TestTemplate
  public void shouldSortByTenantIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().asc(),
        Comparator.comparing(IncidentEntity::tenantId));
  }

  @TestTemplate
  public void shouldSortByCreationTimeAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.creationTime().asc(),
        Comparator.comparing(IncidentEntity::creationTime));
  }

  @TestTemplate
  public void shouldSortByCreationTimeDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.creationTime().desc(),
        Comparator.comparing(IncidentEntity::creationTime).reversed());
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<IncidentSort>> sortBuilder,
      final Comparator<IncidentEntity> comparator) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final IncidentReader reader = rdbmsService.getIncidentReader();

    final var key = nextKey();
    createAndSaveRandomIncidents(rdbmsWriter, b -> b.flowNodeInstanceKey(key));

    final var searchResult =
        reader
            .search(
                new IncidentQuery(
                    new IncidentFilter.Builder().flowNodeInstanceKeys(key).build(),
                    IncidentSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
