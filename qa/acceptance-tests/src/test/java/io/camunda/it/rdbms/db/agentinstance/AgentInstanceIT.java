/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.agentinstance;

import static io.camunda.it.rdbms.db.fixtures.AgentInstanceFixtures.createAndSaveRandomAgentInstance;
import static io.camunda.it.rdbms.db.fixtures.AgentInstanceFixtures.createAndSaveRandomAgentInstances;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import io.camunda.search.filter.AgentInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AgentInstanceQuery;
import io.camunda.search.sort.AgentInstanceSort;
import io.camunda.security.core.reader.ResourceAccessChecks;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AgentInstanceIT {

  @TestTemplate
  public void shouldCreateAndGetAgentInstanceByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final AgentInstanceDbModel model = createAndSaveRandomAgentInstance(testApplication, b -> b);

    final var entity =
        testApplication
            .getRdbmsService()
            .getAgentInstanceDbReader()
            .getByKey(model.agentInstanceKey(), ResourceAccessChecks.disabled());

    assertThat(entity).isNotNull();
    assertThat(entity.agentInstanceKey()).isEqualTo(model.agentInstanceKey());
    assertFieldsMatch(model, entity);
  }

  @TestTemplate
  public void shouldReturnNullForUnknownKey(final CamundaRdbmsTestApplication testApplication) {
    final var entity =
        testApplication
            .getRdbmsService()
            .getAgentInstanceDbReader()
            .getByKey(Long.MIN_VALUE, ResourceAccessChecks.disabled());

    assertThat(entity).isNull();
  }

  @TestTemplate
  public void shouldFindAllAgentInstancesPaged(final CamundaRdbmsTestApplication testApplication) {
    final String processId = "process-paged-" + nextStringId();
    createAndSaveRandomAgentInstances(testApplication, 20, b -> b.processDefinitionId(processId));

    final var result =
        testApplication
            .getRdbmsService()
            .getAgentInstanceDbReader()
            .search(
                new AgentInstanceQuery(
                    new AgentInstanceFilter.Builder().processDefinitionIds(processId).build(),
                    AgentInstanceSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))),
                ResourceAccessChecks.disabled());

    assertThat(result).isNotNull();
    assertThat(result.total()).isEqualTo(20);
    assertThat(result.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldReturnEmptyResultForPageSizeZero(
      final CamundaRdbmsTestApplication testApplication) {
    final String processId = "process-zero-" + nextStringId();
    createAndSaveRandomAgentInstances(testApplication, 3, b -> b.processDefinitionId(processId));

    final var result =
        testApplication
            .getRdbmsService()
            .getAgentInstanceDbReader()
            .search(
                new AgentInstanceQuery(
                    new AgentInstanceFilter.Builder().processDefinitionIds(processId).build(),
                    AgentInstanceSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(0))),
                ResourceAccessChecks.disabled());

    assertThat(result.total()).isEqualTo(3);
    assertThat(result.items()).isEmpty();
  }

  private void assertFieldsMatch(
      final AgentInstanceDbModel dbModel, final AgentInstanceEntity entity) {
    assertThat(entity.agentInstanceKey()).isEqualTo(dbModel.agentInstanceKey());
    assertThat(entity.elementId()).isEqualTo(dbModel.elementId());
    assertThat(entity.processInstanceKey()).isEqualTo(dbModel.processInstanceKey());
    assertThat(entity.processDefinitionKey()).isEqualTo(dbModel.processDefinitionKey());
    assertThat(entity.processDefinitionId()).isEqualTo(dbModel.processDefinitionId());
    assertThat(entity.tenantId()).isEqualTo(dbModel.tenantId());
    assertThat(entity.status())
        .isEqualTo(dbModel.status() != null ? dbModel.status() : AgentInstanceStatus.UNKNOWN);
    assertThat(entity.definition().model()).isEqualTo(dbModel.model());
    assertThat(entity.definition().provider()).isEqualTo(dbModel.provider());
    assertThat(entity.definition().systemPrompt()).isEqualTo(dbModel.systemPrompt());
    assertThat(entity.limits().maxTokens()).isEqualTo(dbModel.maxTokens());
    assertThat(entity.limits().maxModelCalls()).isEqualTo(dbModel.maxModelCalls());
    assertThat(entity.limits().maxToolCalls()).isEqualTo(dbModel.maxToolCalls());
    assertThat(entity.metrics().inputTokens()).isEqualTo(dbModel.inputTokens());
    assertThat(entity.metrics().outputTokens()).isEqualTo(dbModel.outputTokens());
    assertThat(entity.metrics().modelCalls()).isEqualTo(dbModel.modelCalls());
    assertThat(entity.metrics().toolCalls()).isEqualTo(dbModel.toolCalls());
  }
}
