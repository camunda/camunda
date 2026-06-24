/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.agentinstance;

import static io.camunda.it.rdbms.db.fixtures.AgentInstanceFixtures.createAndSaveRandomAgentInstances;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import io.camunda.search.filter.AgentInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AgentInstanceQuery;
import io.camunda.search.sort.AgentInstanceSort;
import io.camunda.search.sort.AgentInstanceSort.Builder;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AgentInstanceSortIT {

  @TestTemplate
  public void shouldSortByCreationDateAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.creationDate().asc(),
        Comparator.comparing(AgentInstanceEntity::creationDate));
  }

  @TestTemplate
  public void shouldSortByCreationDateDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.creationDate().desc(),
        Comparator.comparing(AgentInstanceEntity::creationDate).reversed());
  }

  @TestTemplate
  public void shouldSortByLastUpdatedDateAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.lastUpdatedDate().asc(),
        Comparator.comparing(AgentInstanceEntity::lastUpdatedDate));
  }

  @TestTemplate
  public void shouldSortByLastUpdatedDateDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.lastUpdatedDate().desc(),
        Comparator.comparing(AgentInstanceEntity::lastUpdatedDate).reversed());
  }

  @TestTemplate
  public void shouldSortByStatusAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication, b -> b.status().asc(), Comparator.comparing(e -> e.status().name()));
  }

  @TestTemplate
  public void shouldSortByStatusDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.status().desc(),
        Comparator.comparing((AgentInstanceEntity e) -> e.status().name()).reversed());
  }

  @TestTemplate
  public void shouldSortByAgentInstanceKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.agentInstanceKey().asc(),
        Comparator.comparingLong(AgentInstanceEntity::agentInstanceKey));
  }

  @TestTemplate
  public void shouldSortByAgentInstanceKeyDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.agentInstanceKey().desc(),
        Comparator.comparingLong(AgentInstanceEntity::agentInstanceKey).reversed());
  }

  @TestTemplate
  public void shouldSortByProcessInstanceKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.processInstanceKey().asc(),
        Comparator.comparingLong(AgentInstanceEntity::processInstanceKey));
  }

  @TestTemplate
  public void shouldSortByProcessInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.processInstanceKey().desc(),
        Comparator.comparingLong(AgentInstanceEntity::processInstanceKey).reversed());
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.processDefinitionKey().asc(),
        Comparator.comparingLong(AgentInstanceEntity::processDefinitionKey));
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.processDefinitionKey().desc(),
        Comparator.comparingLong(AgentInstanceEntity::processDefinitionKey).reversed());
  }

  @TestTemplate
  public void shouldSortByElementIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.elementId().asc(),
        Comparator.comparing(AgentInstanceEntity::elementId));
  }

  @TestTemplate
  public void shouldSortByElementIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.elementId().desc(),
        Comparator.comparing(AgentInstanceEntity::elementId).reversed());
  }

  @TestTemplate
  public void shouldSortByTenantIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.tenantId().asc(),
        Comparator.comparing(AgentInstanceEntity::tenantId));
  }

  @TestTemplate
  public void shouldSortByTenantIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.tenantId().desc(),
        Comparator.comparing(AgentInstanceEntity::tenantId).reversed());
  }

  @TestTemplate
  public void shouldSortByRootProcessInstanceKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.rootProcessInstanceKey().asc(),
        Comparator.comparingLong(AgentInstanceEntity::rootProcessInstanceKey));
  }

  @TestTemplate
  public void shouldSortByRootProcessInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.rootProcessInstanceKey().desc(),
        Comparator.comparingLong(AgentInstanceEntity::rootProcessInstanceKey).reversed());
  }

  private void testSorting(
      final CamundaRdbmsTestApplication testApplication,
      final Function<Builder, ObjectBuilder<AgentInstanceSort>> sortBuilder,
      final Comparator<AgentInstanceEntity> comparator) {
    final String procDefId = "proc-sort-" + nextStringId();

    // Create instances with distinct timestamps so sort is deterministic
    final var now = OffsetDateTime.now();
    createAndSaveRandomAgentInstances(
        testApplication,
        5,
        b ->
            b.processDefinitionId(procDefId)
                .status(AgentInstanceStatus.IDLE)
                .creationDate(now)
                .lastUpdatedDate(now));

    final var items =
        testApplication
            .getRdbmsService()
            .getAgentInstanceDbReader()
            .search(
                new AgentInstanceQuery(
                    new AgentInstanceFilter.Builder().processDefinitionIds(procDefId).build(),
                    AgentInstanceSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b.from(0).size(20))),
                ResourceAccessChecks.disabled())
            .items();

    assertThat(items).hasSize(5);
    assertThat(items).isSortedAccordingTo(comparator);
  }
}
