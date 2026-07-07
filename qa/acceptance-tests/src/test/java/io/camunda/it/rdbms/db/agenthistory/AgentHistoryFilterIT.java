/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.agenthistory;

import static io.camunda.it.rdbms.db.fixtures.AgentHistoryFixtures.createAndSaveRandomAgentHistoryItem;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import io.camunda.search.filter.AgentInstanceHistoryFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AgentInstanceHistoryQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.AgentInstanceHistorySort;
import io.camunda.security.core.authz.ResourceAccessChecks;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AgentHistoryFilterIT {

  @TestTemplate
  public void shouldFilterByAgentInstanceKey(final CamundaRdbmsTestApplication testApplication) {
    final long agentInstanceKey = nextKey();
    final var model =
        createAndSaveRandomAgentHistoryItem(
            testApplication, b -> b.agentInstanceKey(agentInstanceKey));
    createAndSaveRandomAgentHistoryItem(testApplication, b -> b);

    final var result =
        search(
            testApplication,
            new AgentInstanceHistoryFilter.Builder().agentInstanceKeys(agentInstanceKey).build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().historyItemKey()).isEqualTo(model.agentHistoryKey());
  }

  @TestTemplate
  public void shouldFilterByHistoryItemKey(final CamundaRdbmsTestApplication testApplication) {
    final var model = createAndSaveRandomAgentHistoryItem(testApplication, b -> b);
    createAndSaveRandomAgentHistoryItem(testApplication, b -> b);

    final var result =
        search(
            testApplication,
            new AgentInstanceHistoryFilter.Builder()
                .historyItemKeys(model.agentHistoryKey())
                .build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().historyItemKey()).isEqualTo(model.agentHistoryKey());
  }

  @TestTemplate
  public void shouldFilterByRole(final CamundaRdbmsTestApplication testApplication) {
    final long agentInstanceKey = nextKey();
    createAndSaveRandomAgentHistoryItem(
        testApplication,
        b -> b.agentInstanceKey(agentInstanceKey).role(AgentInstanceHistoryRole.USER));
    createAndSaveRandomAgentHistoryItem(
        testApplication,
        b -> b.agentInstanceKey(agentInstanceKey).role(AgentInstanceHistoryRole.ASSISTANT));
    createAndSaveRandomAgentHistoryItem(
        testApplication,
        b -> b.agentInstanceKey(agentInstanceKey).role(AgentInstanceHistoryRole.ASSISTANT));

    final var result =
        search(
            testApplication,
            new AgentInstanceHistoryFilter.Builder()
                .agentInstanceKeys(agentInstanceKey)
                .roles(AgentInstanceHistoryRole.ASSISTANT)
                .build());

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).allMatch(e -> e.role() == AgentInstanceHistoryRole.ASSISTANT);
  }

  @TestTemplate
  public void shouldFilterByElementInstanceKey(final CamundaRdbmsTestApplication testApplication) {
    final long elementInstanceKey = nextKey();
    final var model =
        createAndSaveRandomAgentHistoryItem(
            testApplication, b -> b.elementInstanceKey(elementInstanceKey));
    createAndSaveRandomAgentHistoryItem(testApplication, b -> b);

    final var result =
        search(
            testApplication,
            new AgentInstanceHistoryFilter.Builder()
                .elementInstanceKeys(elementInstanceKey)
                .build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().historyItemKey()).isEqualTo(model.agentHistoryKey());
  }

  @TestTemplate
  public void shouldFilterByJobKey(final CamundaRdbmsTestApplication testApplication) {
    final long jobKey = nextKey();
    final var model = createAndSaveRandomAgentHistoryItem(testApplication, b -> b.jobKey(jobKey));
    createAndSaveRandomAgentHistoryItem(testApplication, b -> b);

    final var result =
        search(testApplication, new AgentInstanceHistoryFilter.Builder().jobKeys(jobKey).build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().historyItemKey()).isEqualTo(model.agentHistoryKey());
  }

  @TestTemplate
  public void shouldFilterByIteration(final CamundaRdbmsTestApplication testApplication) {
    final long agentInstanceKey = nextKey();
    createAndSaveRandomAgentHistoryItem(
        testApplication, b -> b.agentInstanceKey(agentInstanceKey).iteration(1));
    createAndSaveRandomAgentHistoryItem(
        testApplication, b -> b.agentInstanceKey(agentInstanceKey).iteration(2));
    createAndSaveRandomAgentHistoryItem(
        testApplication, b -> b.agentInstanceKey(agentInstanceKey).iteration(2));

    final var result =
        search(
            testApplication,
            new AgentInstanceHistoryFilter.Builder()
                .agentInstanceKeys(agentInstanceKey)
                .iterations(2)
                .build());

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).allMatch(e -> e.iteration() == 2);
  }

  @TestTemplate
  public void shouldFilterByCommitStatus(final CamundaRdbmsTestApplication testApplication) {
    final long agentInstanceKey = nextKey();
    createAndSaveRandomAgentHistoryItem(
        testApplication,
        b ->
            b.agentInstanceKey(agentInstanceKey)
                .commitStatus(AgentInstanceHistoryCommitStatus.COMMITTED));
    createAndSaveRandomAgentHistoryItem(
        testApplication,
        b ->
            b.agentInstanceKey(agentInstanceKey)
                .commitStatus(AgentInstanceHistoryCommitStatus.PENDING));
    createAndSaveRandomAgentHistoryItem(
        testApplication,
        b ->
            b.agentInstanceKey(agentInstanceKey)
                .commitStatus(AgentInstanceHistoryCommitStatus.DISCARDED));

    final var result =
        search(
            testApplication,
            new AgentInstanceHistoryFilter.Builder()
                .agentInstanceKeys(agentInstanceKey)
                .commitStatuses(AgentInstanceHistoryCommitStatus.COMMITTED)
                .build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().commitStatus())
        .isEqualTo(AgentInstanceHistoryCommitStatus.COMMITTED);
  }

  @TestTemplate
  public void shouldFilterByProducedAt(final CamundaRdbmsTestApplication testApplication) {
    final long agentInstanceKey = nextKey();
    final OffsetDateTime base = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusDays(10);

    createAndSaveRandomAgentHistoryItem(
        testApplication, b -> b.agentInstanceKey(agentInstanceKey).producedAt(base.minusDays(1)));
    createAndSaveRandomAgentHistoryItem(
        testApplication, b -> b.agentInstanceKey(agentInstanceKey).producedAt(base));
    createAndSaveRandomAgentHistoryItem(
        testApplication, b -> b.agentInstanceKey(agentInstanceKey).producedAt(base.plusDays(1)));

    final var result =
        search(
            testApplication,
            new AgentInstanceHistoryFilter.Builder()
                .agentInstanceKeys(agentInstanceKey)
                .producedAtOperations(Operation.gte(base))
                .build());

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).allMatch(e -> !e.producedAt().isBefore(base));
  }

  private SearchQueryResult<AgentInstanceHistoryEntity> search(
      final CamundaRdbmsTestApplication testApplication, final AgentInstanceHistoryFilter filter) {
    return testApplication
        .getRdbmsService()
        .getAgentHistoryDbReader()
        .search(
            new AgentInstanceHistoryQuery(
                filter, AgentInstanceHistorySort.of(b -> b), SearchQueryPage.of(b -> b)),
            ResourceAccessChecks.disabled());
  }
}
