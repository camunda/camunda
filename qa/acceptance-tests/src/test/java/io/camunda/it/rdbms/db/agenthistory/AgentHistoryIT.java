/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.agenthistory;

import static io.camunda.it.rdbms.db.fixtures.AgentHistoryFixtures.createAndSaveRandomAgentHistoryItem;
import static io.camunda.it.rdbms.db.fixtures.AgentHistoryFixtures.createAndSaveRandomAgentHistoryItems;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.AgentHistoryDbModel;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.filter.AgentInstanceHistoryFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AgentInstanceHistoryQuery;
import io.camunda.search.sort.AgentInstanceHistorySort;
import io.camunda.security.core.authz.ResourceAccessChecks;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AgentHistoryIT {

  @TestTemplate
  public void shouldFindItemByAgentInstanceKey(final CamundaRdbmsTestApplication testApplication) {
    final String procDefId = "proc-find-" + nextStringId();
    final AgentHistoryDbModel model =
        createAndSaveRandomAgentHistoryItem(testApplication, b -> b.processDefinitionId(procDefId));
    createAndSaveRandomAgentHistoryItem(testApplication, b -> b.processDefinitionId(procDefId));

    final var result =
        testApplication
            .getRdbmsService()
            .getAgentHistoryDbReader()
            .search(
                new AgentInstanceHistoryQuery(
                    new AgentInstanceHistoryFilter.Builder()
                        .agentInstanceKeys(model.agentInstanceKey())
                        .build(),
                    AgentInstanceHistorySort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))),
                ResourceAccessChecks.disabled());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().historyItemKey()).isEqualTo(model.agentHistoryKey());
    assertFieldsMatch(model, result.items().getFirst());
  }

  @TestTemplate
  public void shouldReturnEmptyResultForUnknownKey(
      final CamundaRdbmsTestApplication testApplication) {
    final var result =
        testApplication
            .getRdbmsService()
            .getAgentHistoryDbReader()
            .search(
                new AgentInstanceHistoryQuery(
                    new AgentInstanceHistoryFilter.Builder()
                        .agentInstanceKeys(Long.MIN_VALUE)
                        .build(),
                    AgentInstanceHistorySort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))),
                ResourceAccessChecks.disabled());

    assertThat(result.total()).isEqualTo(0);
    assertThat(result.items()).isEmpty();
  }

  @TestTemplate
  public void shouldFindAllItemsPaged(final CamundaRdbmsTestApplication testApplication) {
    final long agentInstanceKey = io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey();
    createAndSaveRandomAgentHistoryItems(
        testApplication, 7, b -> b.agentInstanceKey(agentInstanceKey));

    final var result =
        testApplication
            .getRdbmsService()
            .getAgentHistoryDbReader()
            .search(
                new AgentInstanceHistoryQuery(
                    new AgentInstanceHistoryFilter.Builder()
                        .agentInstanceKeys(agentInstanceKey)
                        .build(),
                    AgentInstanceHistorySort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))),
                ResourceAccessChecks.disabled());

    assertThat(result.total()).isEqualTo(7);
    assertThat(result.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldReturnEmptyItemsButCorrectTotalForPageSizeZero(
      final CamundaRdbmsTestApplication testApplication) {
    final String procDefId = "proc-zero-" + nextStringId();
    final long agentInstanceKey = io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey();
    createAndSaveRandomAgentHistoryItems(
        testApplication,
        3,
        b -> b.processDefinitionId(procDefId).agentInstanceKey(agentInstanceKey));

    final var result =
        testApplication
            .getRdbmsService()
            .getAgentHistoryDbReader()
            .search(
                new AgentInstanceHistoryQuery(
                    new AgentInstanceHistoryFilter.Builder()
                        .agentInstanceKeys(agentInstanceKey)
                        .build(),
                    AgentInstanceHistorySort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(0))),
                ResourceAccessChecks.disabled());

    assertThat(result.total()).isEqualTo(3);
    assertThat(result.items()).isEmpty();
  }

  @TestTemplate
  public void shouldPersistCommitStatusUpdate(final CamundaRdbmsTestApplication testApplication) {
    final AgentHistoryDbModel model = createAndSaveRandomAgentHistoryItem(testApplication, b -> b);

    final var updated =
        model.copy(
            b ->
                ((AgentHistoryDbModel.Builder) b)
                    .commitStatus(AgentInstanceHistoryCommitStatus.COMMITTED));
    final var writer = testApplication.getRdbmsService().createWriter(0);
    writer.getAgentHistoryWriter().updateCommitStatus(updated);
    writer.flush();

    final var result =
        testApplication
            .getRdbmsService()
            .getAgentHistoryDbReader()
            .search(
                new AgentInstanceHistoryQuery(
                    new AgentInstanceHistoryFilter.Builder()
                        .agentInstanceKeys(model.agentInstanceKey())
                        .build(),
                    AgentInstanceHistorySort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(1))),
                ResourceAccessChecks.disabled());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().commitStatus())
        .isEqualTo(AgentInstanceHistoryCommitStatus.COMMITTED);
  }

  private void assertFieldsMatch(
      final AgentHistoryDbModel dbModel, final AgentInstanceHistoryEntity entity) {
    assertThat(entity.historyItemKey()).isEqualTo(dbModel.agentHistoryKey());
    assertThat(entity.agentInstanceKey()).isEqualTo(dbModel.agentInstanceKey());
    assertThat(entity.elementInstanceKey()).isEqualTo(dbModel.elementInstanceKey());
    assertThat(entity.processInstanceKey()).isEqualTo(dbModel.processInstanceKey());
    assertThat(entity.processDefinitionKey()).isEqualTo(dbModel.processDefinitionKey());
    assertThat(entity.processDefinitionId()).isEqualTo(dbModel.processDefinitionId());
    assertThat(entity.tenantId()).isEqualTo(dbModel.tenantId());
    assertThat(entity.jobKey()).isEqualTo(dbModel.jobKey());
    assertThat(entity.iteration()).isEqualTo(dbModel.iteration());
    assertThat(entity.role()).isEqualTo(dbModel.role());
    assertThat(entity.commitStatus()).isEqualTo(dbModel.commitStatus());
    assertThat(entity.metrics().inputTokens()).isEqualTo(dbModel.inputTokens());
    assertThat(entity.metrics().outputTokens()).isEqualTo(dbModel.outputTokens());
    assertThat(entity.metrics().durationMs()).isEqualTo(dbModel.durationMs());
  }
}
