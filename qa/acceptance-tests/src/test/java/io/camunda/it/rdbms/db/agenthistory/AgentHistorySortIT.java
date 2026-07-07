/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.agenthistory;

import static io.camunda.it.rdbms.db.fixtures.AgentHistoryFixtures.createAndSaveRandomAgentHistoryItems;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.filter.AgentInstanceHistoryFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AgentInstanceHistoryQuery;
import io.camunda.search.sort.AgentInstanceHistorySort;
import io.camunda.search.sort.AgentInstanceHistorySort.Builder;
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
public class AgentHistorySortIT {

  @TestTemplate
  public void shouldSortByHistoryItemKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.historyItemKey().asc(),
        Comparator.comparingLong(AgentInstanceHistoryEntity::historyItemKey));
  }

  @TestTemplate
  public void shouldSortByHistoryItemKeyDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.historyItemKey().desc(),
        Comparator.comparingLong(AgentInstanceHistoryEntity::historyItemKey).reversed());
  }

  @TestTemplate
  public void shouldSortByIterationAsc(final CamundaRdbmsTestApplication testApplication) {
    testSortingWithDistinctIterations(
        testApplication,
        b -> b.iteration().asc(),
        Comparator.comparingInt(AgentInstanceHistoryEntity::iteration));
  }

  @TestTemplate
  public void shouldSortByIterationDesc(final CamundaRdbmsTestApplication testApplication) {
    testSortingWithDistinctIterations(
        testApplication,
        b -> b.iteration().desc(),
        Comparator.comparingInt(AgentInstanceHistoryEntity::iteration).reversed());
  }

  @TestTemplate
  public void shouldSortByProducedAtAsc(final CamundaRdbmsTestApplication testApplication) {
    testSortingWithDistinctProducedAt(
        testApplication,
        b -> b.producedAt().asc(),
        Comparator.comparing(AgentInstanceHistoryEntity::producedAt));
  }

  @TestTemplate
  public void shouldSortByProducedAtDesc(final CamundaRdbmsTestApplication testApplication) {
    testSortingWithDistinctProducedAt(
        testApplication,
        b -> b.producedAt().desc(),
        Comparator.comparing(AgentInstanceHistoryEntity::producedAt).reversed());
  }

  private void testSorting(
      final CamundaRdbmsTestApplication testApplication,
      final Function<Builder, ObjectBuilder<AgentInstanceHistorySort>> sortBuilder,
      final Comparator<AgentInstanceHistoryEntity> comparator) {
    final long agentInstanceKey = nextKey();

    createAndSaveRandomAgentHistoryItems(
        testApplication, 5, b -> b.agentInstanceKey(agentInstanceKey));

    final var items = search(testApplication, agentInstanceKey, sortBuilder);

    assertThat(items).hasSize(5);
    assertThat(items).isSortedAccordingTo(comparator);
  }

  private void testSortingWithDistinctIterations(
      final CamundaRdbmsTestApplication testApplication,
      final Function<Builder, ObjectBuilder<AgentInstanceHistorySort>> sortBuilder,
      final Comparator<AgentInstanceHistoryEntity> comparator) {
    final long agentInstanceKey = nextKey();
    final String procDefId = "proc-iter-sort-" + nextStringId();

    for (int i = 1; i <= 5; i++) {
      final int iteration = i;
      createAndSaveRandomAgentHistoryItems(
          testApplication,
          1,
          b ->
              b.agentInstanceKey(agentInstanceKey)
                  .processDefinitionId(procDefId)
                  .iteration(iteration));
    }

    final var items = search(testApplication, agentInstanceKey, sortBuilder);

    assertThat(items).hasSize(5);
    assertThat(items).isSortedAccordingTo(comparator);
  }

  private void testSortingWithDistinctProducedAt(
      final CamundaRdbmsTestApplication testApplication,
      final Function<Builder, ObjectBuilder<AgentInstanceHistorySort>> sortBuilder,
      final Comparator<AgentInstanceHistoryEntity> comparator) {
    final long agentInstanceKey = nextKey();
    final OffsetDateTime base = OffsetDateTime.now().minusDays(5);

    for (int i = 0; i < 5; i++) {
      final OffsetDateTime producedAt = base.plusDays(i);
      createAndSaveRandomAgentHistoryItems(
          testApplication, 1, b -> b.agentInstanceKey(agentInstanceKey).producedAt(producedAt));
    }

    final var items = search(testApplication, agentInstanceKey, sortBuilder);

    assertThat(items).hasSize(5);
    assertThat(items).isSortedAccordingTo(comparator);
  }

  private java.util.List<AgentInstanceHistoryEntity> search(
      final CamundaRdbmsTestApplication testApplication,
      final long agentInstanceKey,
      final Function<Builder, ObjectBuilder<AgentInstanceHistorySort>> sortBuilder) {
    return testApplication
        .getRdbmsService()
        .getAgentHistoryDbReader()
        .search(
            new AgentInstanceHistoryQuery(
                new AgentInstanceHistoryFilter.Builder()
                    .agentInstanceKeys(agentInstanceKey)
                    .build(),
                AgentInstanceHistorySort.of(sortBuilder),
                SearchQueryPage.of(b -> b.from(0).size(20))),
            ResourceAccessChecks.disabled())
        .items();
  }
}
