/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.agentdefinition;

import static io.camunda.it.rdbms.db.fixtures.AgentDefinitionFixtures.createAndSaveRandomAgentDefinition;
import static io.camunda.it.rdbms.db.fixtures.AgentDefinitionFixtures.createAndSaveRandomAgentDefinitions;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AgentDefinitionEntity;
import io.camunda.search.entities.AgentDefinitionEntity.AgentType;
import io.camunda.search.filter.AgentDefinitionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AgentDefinitionQuery;
import io.camunda.search.sort.AgentDefinitionSort;
import io.camunda.search.sort.AgentDefinitionSort.Builder;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AgentDefinitionSortIT {

  @TestTemplate
  public void shouldSortByAgentDefinitionKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSortingByTenantIdMarker(
        testApplication,
        b -> b.agentDefinitionKey().asc(),
        Comparator.comparingLong(AgentDefinitionEntity::agentDefinitionKey));
  }

  @TestTemplate
  public void shouldSortByAgentDefinitionKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSortingByTenantIdMarker(
        testApplication,
        b -> b.agentDefinitionKey().desc(),
        Comparator.comparingLong(AgentDefinitionEntity::agentDefinitionKey).reversed());
  }

  @TestTemplate
  public void shouldSortByAgentTypeAsc(final CamundaRdbmsTestApplication testApplication) {
    testSortingByAgentType(
        testApplication, b -> b.agentType().asc(), Comparator.comparing(e -> e.agentType().name()));
  }

  @TestTemplate
  public void shouldSortByAgentTypeDesc(final CamundaRdbmsTestApplication testApplication) {
    testSortingByAgentType(
        testApplication,
        b -> b.agentType().desc(),
        Comparator.comparing((AgentDefinitionEntity e) -> e.agentType().name()).reversed());
  }

  @TestTemplate
  public void shouldSortByNameAsc(final CamundaRdbmsTestApplication testApplication) {
    testSortingByTenantIdMarker(
        testApplication, b -> b.name().asc(), Comparator.comparing(AgentDefinitionEntity::name));
  }

  @TestTemplate
  public void shouldSortByNameDesc(final CamundaRdbmsTestApplication testApplication) {
    testSortingByTenantIdMarker(
        testApplication,
        b -> b.name().desc(),
        Comparator.comparing(AgentDefinitionEntity::name).reversed());
  }

  @TestTemplate
  public void shouldSortByElementIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSortingByTenantIdMarker(
        testApplication,
        b -> b.elementId().asc(),
        Comparator.comparing(AgentDefinitionEntity::elementId));
  }

  @TestTemplate
  public void shouldSortByElementIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSortingByTenantIdMarker(
        testApplication,
        b -> b.elementId().desc(),
        Comparator.comparing(AgentDefinitionEntity::elementId).reversed());
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionIdAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSortingByTenantIdMarker(
        testApplication,
        b -> b.processDefinitionId().asc(),
        Comparator.comparing(AgentDefinitionEntity::processDefinitionId));
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionIdDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSortingByTenantIdMarker(
        testApplication,
        b -> b.processDefinitionId().desc(),
        Comparator.comparing(AgentDefinitionEntity::processDefinitionId).reversed());
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSortingByTenantIdMarker(
        testApplication,
        b -> b.processDefinitionKey().asc(),
        Comparator.comparingLong(AgentDefinitionEntity::processDefinitionKey));
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSortingByTenantIdMarker(
        testApplication,
        b -> b.processDefinitionKey().desc(),
        Comparator.comparingLong(AgentDefinitionEntity::processDefinitionKey).reversed());
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionVersionAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSortingByProcessDefinitionVersion(
        testApplication,
        b -> b.processDefinitionVersion().asc(),
        Comparator.comparingInt(AgentDefinitionEntity::processDefinitionVersion));
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionVersionDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSortingByProcessDefinitionVersion(
        testApplication,
        b -> b.processDefinitionVersion().desc(),
        Comparator.comparingInt(AgentDefinitionEntity::processDefinitionVersion).reversed());
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionVersionTagAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSortingByTenantIdMarker(
        testApplication,
        b -> b.processDefinitionVersionTag().asc(),
        Comparator.comparing(AgentDefinitionEntity::processDefinitionVersionTag));
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionVersionTagDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSortingByTenantIdMarker(
        testApplication,
        b -> b.processDefinitionVersionTag().desc(),
        Comparator.comparing(AgentDefinitionEntity::processDefinitionVersionTag).reversed());
  }

  @TestTemplate
  public void shouldSortByTenantIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSortingByProcessDefinitionIdMarker(
        testApplication,
        b -> b.tenantId().asc(),
        Comparator.comparing(AgentDefinitionEntity::tenantId));
  }

  @TestTemplate
  public void shouldSortByTenantIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSortingByProcessDefinitionIdMarker(
        testApplication,
        b -> b.tenantId().desc(),
        Comparator.comparing(AgentDefinitionEntity::tenantId).reversed());
  }

  /**
   * Seeds 5 definitions sharing one tenantId (used purely to isolate this test's rows from other
   * tests' data) while the field under test keeps the distinct value the fixture already assigns
   * per created row.
   */
  private void testSortingByTenantIdMarker(
      final CamundaRdbmsTestApplication testApplication,
      final Function<Builder, ObjectBuilder<AgentDefinitionSort>> sortBuilder,
      final Comparator<AgentDefinitionEntity> comparator) {
    final String tenantId = "tenant-sort-" + nextStringId();
    createAndSaveRandomAgentDefinitions(testApplication, 5, b -> b.tenantId(tenantId));

    final var items =
        search(
            testApplication,
            new AgentDefinitionFilter.Builder().tenantIds(tenantId).build(),
            sortBuilder);

    assertThat(items).hasSize(5);
    assertThat(items).isSortedAccordingTo(comparator);
  }

  /**
   * Mirror of {@link #testSortingByTenantIdMarker} for the tenantId sort itself: isolates via a
   * fixed processDefinitionId while tenantId keeps its distinct per-row default value.
   */
  private void testSortingByProcessDefinitionIdMarker(
      final CamundaRdbmsTestApplication testApplication,
      final Function<Builder, ObjectBuilder<AgentDefinitionSort>> sortBuilder,
      final Comparator<AgentDefinitionEntity> comparator) {
    final String processDefinitionId = "process-sort-" + nextStringId();
    createAndSaveRandomAgentDefinitions(
        testApplication, 5, b -> b.processDefinitionId(processDefinitionId));

    final var items =
        search(
            testApplication,
            new AgentDefinitionFilter.Builder().processDefinitionIds(processDefinitionId).build(),
            sortBuilder);

    assertThat(items).hasSize(5);
    assertThat(items).isSortedAccordingTo(comparator);
  }

  /**
   * agentType only has 3 valid values, so the fixture's random default cannot be relied on for
   * distinctness across a batch. Seed exactly one definition per {@link AgentType} instead.
   */
  private void testSortingByAgentType(
      final CamundaRdbmsTestApplication testApplication,
      final Function<Builder, ObjectBuilder<AgentDefinitionSort>> sortBuilder,
      final Comparator<AgentDefinitionEntity> comparator) {
    final String tenantId = "tenant-sort-" + nextStringId();
    for (final AgentType type : AgentType.values()) {
      createAndSaveRandomAgentDefinition(
          testApplication, b -> b.tenantId(tenantId).agentType(type));
    }

    final var items =
        search(
            testApplication,
            new AgentDefinitionFilter.Builder().tenantIds(tenantId).build(),
            sortBuilder);

    assertThat(items).hasSize(AgentType.values().length);
    assertThat(items).isSortedAccordingTo(comparator);
  }

  /**
   * processDefinitionVersion is always 1 in the fixture default, so it must be assigned distinct
   * values explicitly per row for the sort order to be unambiguous.
   */
  private void testSortingByProcessDefinitionVersion(
      final CamundaRdbmsTestApplication testApplication,
      final Function<Builder, ObjectBuilder<AgentDefinitionSort>> sortBuilder,
      final Comparator<AgentDefinitionEntity> comparator) {
    final String tenantId = "tenant-sort-" + nextStringId();
    for (int i = 0; i < 5; i++) {
      final int version = i + 1;
      createAndSaveRandomAgentDefinition(
          testApplication, b -> b.tenantId(tenantId).processDefinitionVersion(version));
    }

    final var items =
        search(
            testApplication,
            new AgentDefinitionFilter.Builder().tenantIds(tenantId).build(),
            sortBuilder);

    assertThat(items).hasSize(5);
    assertThat(items).isSortedAccordingTo(comparator);
  }

  private List<AgentDefinitionEntity> search(
      final CamundaRdbmsTestApplication testApplication,
      final AgentDefinitionFilter filter,
      final Function<Builder, ObjectBuilder<AgentDefinitionSort>> sortBuilder) {
    return testApplication
        .getRdbmsService()
        .getAgentDefinitionDbReader()
        .search(
            new AgentDefinitionQuery(
                filter,
                AgentDefinitionSort.of(sortBuilder),
                SearchQueryPage.of(b -> b.from(0).size(20))),
            ResourceAccessChecks.disabled())
        .items();
  }
}
