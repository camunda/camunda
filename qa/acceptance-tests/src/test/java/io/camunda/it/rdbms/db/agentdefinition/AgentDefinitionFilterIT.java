/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.agentdefinition;

import static io.camunda.it.rdbms.db.fixtures.AgentDefinitionFixtures.createAndSaveRandomAgentDefinition;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AgentDefinitionEntity;
import io.camunda.search.entities.AgentDefinitionEntity.AgentType;
import io.camunda.search.filter.AgentDefinitionFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AgentDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.AgentDefinitionSort;
import io.camunda.security.core.authz.ResourceAccessChecks;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AgentDefinitionFilterIT {

  @TestTemplate
  public void shouldFilterByAgentDefinitionKey(final CamundaRdbmsTestApplication testApplication) {
    final var model = createAndSaveRandomAgentDefinition(testApplication, b -> b);
    createAndSaveRandomAgentDefinition(testApplication, b -> b);

    final var result =
        search(
            testApplication,
            new AgentDefinitionFilter.Builder()
                .agentDefinitionKeys(model.agentDefinitionKey())
                .build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().agentDefinitionKey())
        .isEqualTo(model.agentDefinitionKey());
  }

  @TestTemplate
  public void shouldFilterByAgentDefinitionKeyIn(
      final CamundaRdbmsTestApplication testApplication) {
    final var model1 = createAndSaveRandomAgentDefinition(testApplication, b -> b);
    final var model2 = createAndSaveRandomAgentDefinition(testApplication, b -> b);
    createAndSaveRandomAgentDefinition(testApplication, b -> b);

    final var result =
        search(
            testApplication,
            new AgentDefinitionFilter.Builder()
                .agentDefinitionKeyOperations(
                    Operation.in(model1.agentDefinitionKey(), model2.agentDefinitionKey()))
                .build());

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items())
        .extracting(AgentDefinitionEntity::agentDefinitionKey)
        .containsExactlyInAnyOrder(model1.agentDefinitionKey(), model2.agentDefinitionKey());
  }

  @TestTemplate
  public void shouldFilterByNameLike(final CamundaRdbmsTestApplication testApplication) {
    final String tenantId = "tenant-name-like-" + nextStringId();
    final var model1 =
        createAndSaveRandomAgentDefinition(
            testApplication, b -> b.tenantId(tenantId).name("expectedLike1"));
    final var model2 =
        createAndSaveRandomAgentDefinition(
            testApplication, b -> b.tenantId(tenantId).name("expectedLike2"));
    createAndSaveRandomAgentDefinition(
        testApplication, b -> b.tenantId(tenantId).name("different" + nextStringId()));

    final var result =
        search(
            testApplication,
            new AgentDefinitionFilter.Builder()
                .tenantIds(tenantId)
                .nameOperations(Operation.like("expectedLike*"))
                .build());

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items())
        .extracting(AgentDefinitionEntity::agentDefinitionKey)
        .containsExactlyInAnyOrder(model1.agentDefinitionKey(), model2.agentDefinitionKey());
  }

  @TestTemplate
  public void shouldFilterByProcessDefinitionVersionRange(
      final CamundaRdbmsTestApplication testApplication) {
    final String tenantId = "tenant-version-range-" + nextStringId();
    createAndSaveRandomAgentDefinition(
        testApplication, b -> b.tenantId(tenantId).processDefinitionVersion(1));
    final var model =
        createAndSaveRandomAgentDefinition(
            testApplication, b -> b.tenantId(tenantId).processDefinitionVersion(5));
    createAndSaveRandomAgentDefinition(
        testApplication, b -> b.tenantId(tenantId).processDefinitionVersion(9));

    final var result =
        search(
            testApplication,
            new AgentDefinitionFilter.Builder()
                .tenantIds(tenantId)
                .processDefinitionVersionOperations(Operation.gt(2), Operation.lt(8))
                .build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().agentDefinitionKey())
        .isEqualTo(model.agentDefinitionKey());
  }

  @TestTemplate
  public void shouldFilterByAgentType(final CamundaRdbmsTestApplication testApplication) {
    final String tenantId = "tenant-agent-type-" + nextStringId();
    final var model =
        createAndSaveRandomAgentDefinition(
            testApplication, b -> b.tenantId(tenantId).agentType(AgentType.AI_AGENT_TASK));
    createAndSaveRandomAgentDefinition(
        testApplication, b -> b.tenantId(tenantId).agentType(AgentType.EXTERNAL_AGENT));

    final var result =
        search(
            testApplication,
            new AgentDefinitionFilter.Builder()
                .tenantIds(tenantId)
                .agentTypes(AgentType.AI_AGENT_TASK.name())
                .build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().agentDefinitionKey())
        .isEqualTo(model.agentDefinitionKey());
    assertThat(result.items().getFirst().agentType()).isEqualTo(AgentType.AI_AGENT_TASK);
  }

  @TestTemplate
  public void shouldFilterByName(final CamundaRdbmsTestApplication testApplication) {
    final String name = "agent-name-" + nextStringId();
    final var model = createAndSaveRandomAgentDefinition(testApplication, b -> b.name(name));
    createAndSaveRandomAgentDefinition(testApplication, b -> b);

    final var result =
        search(testApplication, new AgentDefinitionFilter.Builder().names(name).build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().agentDefinitionKey())
        .isEqualTo(model.agentDefinitionKey());
    assertThat(result.items().getFirst().name()).isEqualTo(name);
  }

  @TestTemplate
  public void shouldFilterByElementId(final CamundaRdbmsTestApplication testApplication) {
    final String elementId = "Task_specificElement-" + nextStringId();
    final var model =
        createAndSaveRandomAgentDefinition(testApplication, b -> b.elementId(elementId));
    createAndSaveRandomAgentDefinition(testApplication, b -> b);

    final var result =
        search(testApplication, new AgentDefinitionFilter.Builder().elementIds(elementId).build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().agentDefinitionKey())
        .isEqualTo(model.agentDefinitionKey());
    assertThat(result.items().getFirst().elementId()).isEqualTo(elementId);
  }

  @TestTemplate
  public void shouldFilterByProcessDefinitionId(final CamundaRdbmsTestApplication testApplication) {
    final String processDefinitionId = "myProcess-" + nextStringId();
    final var model =
        createAndSaveRandomAgentDefinition(
            testApplication, b -> b.processDefinitionId(processDefinitionId));
    createAndSaveRandomAgentDefinition(testApplication, b -> b);

    final var result =
        search(
            testApplication,
            new AgentDefinitionFilter.Builder().processDefinitionIds(processDefinitionId).build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().agentDefinitionKey())
        .isEqualTo(model.agentDefinitionKey());
    assertThat(result.items().getFirst().processDefinitionId()).isEqualTo(processDefinitionId);
  }

  @TestTemplate
  public void shouldFilterByProcessDefinitionKey(
      final CamundaRdbmsTestApplication testApplication) {
    final long processDefinitionKey = nextKey();
    final var model =
        createAndSaveRandomAgentDefinition(
            testApplication, b -> b.processDefinitionKey(processDefinitionKey));
    createAndSaveRandomAgentDefinition(testApplication, b -> b);

    final var result =
        search(
            testApplication,
            new AgentDefinitionFilter.Builder()
                .processDefinitionKeys(processDefinitionKey)
                .build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().agentDefinitionKey())
        .isEqualTo(model.agentDefinitionKey());
    assertThat(result.items().getFirst().processDefinitionKey()).isEqualTo(processDefinitionKey);
  }

  @TestTemplate
  public void shouldFilterByProcessDefinitionVersion(
      final CamundaRdbmsTestApplication testApplication) {
    final String tenantId = "tenant-version-" + nextStringId();
    final var model =
        createAndSaveRandomAgentDefinition(
            testApplication, b -> b.tenantId(tenantId).processDefinitionVersion(7));
    createAndSaveRandomAgentDefinition(
        testApplication, b -> b.tenantId(tenantId).processDefinitionVersion(8));

    final var result =
        search(
            testApplication,
            new AgentDefinitionFilter.Builder()
                .tenantIds(tenantId)
                .processDefinitionVersions(7)
                .build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().agentDefinitionKey())
        .isEqualTo(model.agentDefinitionKey());
    assertThat(result.items().getFirst().processDefinitionVersion()).isEqualTo(7);
  }

  @TestTemplate
  public void shouldFilterByProcessDefinitionVersionTag(
      final CamundaRdbmsTestApplication testApplication) {
    final String versionTag = "version-tag-" + nextStringId();
    final var model =
        createAndSaveRandomAgentDefinition(
            testApplication, b -> b.processDefinitionVersionTag(versionTag));
    createAndSaveRandomAgentDefinition(testApplication, b -> b);

    final var result =
        search(
            testApplication,
            new AgentDefinitionFilter.Builder().processDefinitionVersionTags(versionTag).build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().agentDefinitionKey())
        .isEqualTo(model.agentDefinitionKey());
    assertThat(result.items().getFirst().processDefinitionVersionTag()).isEqualTo(versionTag);
  }

  @TestTemplate
  public void shouldFilterByTenantId(final CamundaRdbmsTestApplication testApplication) {
    final String tenantId = "tenant-" + nextStringId();
    createAndSaveRandomAgentDefinition(testApplication, b -> b.tenantId(tenantId));
    createAndSaveRandomAgentDefinition(testApplication, b -> b.tenantId(tenantId));
    createAndSaveRandomAgentDefinition(testApplication, b -> b.tenantId("<other>"));

    final var result =
        search(testApplication, new AgentDefinitionFilter.Builder().tenantIds(tenantId).build());

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).allMatch(e -> tenantId.equals(e.tenantId()));
  }

  private SearchQueryResult<AgentDefinitionEntity> search(
      final CamundaRdbmsTestApplication testApplication, final AgentDefinitionFilter filter) {
    return testApplication
        .getRdbmsService()
        .getAgentDefinitionDbReader()
        .search(
            new AgentDefinitionQuery(
                filter, AgentDefinitionSort.of(b -> b), SearchQueryPage.of(b -> b)),
            ResourceAccessChecks.disabled());
  }
}
