/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.agentinstance;

import static io.camunda.it.rdbms.db.fixtures.AgentInstanceFixtures.createAndSaveRandomAgentInstance;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import io.camunda.search.filter.AgentInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AgentInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.AgentInstanceSort;
import io.camunda.security.core.reader.ResourceAccessChecks;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AgentInstanceFilterIT {

  @TestTemplate
  public void shouldFilterByAgentInstanceKey(final CamundaRdbmsTestApplication testApplication) {
    final var model = createAndSaveRandomAgentInstance(testApplication, b -> b);
    createAndSaveRandomAgentInstance(testApplication, b -> b);

    final var result =
        search(
            testApplication,
            new AgentInstanceFilter.Builder().agentInstanceKeys(model.agentInstanceKey()).build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().agentInstanceKey()).isEqualTo(model.agentInstanceKey());
  }

  @TestTemplate
  public void shouldFilterByProcessInstanceKey(final CamundaRdbmsTestApplication testApplication) {
    final long piKey = nextKey();
    final var model =
        createAndSaveRandomAgentInstance(testApplication, b -> b.processInstanceKey(piKey));
    createAndSaveRandomAgentInstance(testApplication, b -> b);

    final var result =
        search(
            testApplication, new AgentInstanceFilter.Builder().processInstanceKeys(piKey).build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().processInstanceKey()).isEqualTo(piKey);
  }

  @TestTemplate
  public void shouldFilterByProcessDefinitionId(final CamundaRdbmsTestApplication testApplication) {
    final String procDefId = "myProcess-" + nextStringId();
    createAndSaveRandomAgentInstance(testApplication, b -> b.processDefinitionId(procDefId));
    createAndSaveRandomAgentInstance(testApplication, b -> b.processDefinitionId(procDefId));
    createAndSaveRandomAgentInstance(testApplication, b -> b);

    final var result =
        search(
            testApplication,
            new AgentInstanceFilter.Builder().processDefinitionIds(procDefId).build());

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).allMatch(e -> procDefId.equals(e.processDefinitionId()));
  }

  @TestTemplate
  public void shouldFilterByStatus(final CamundaRdbmsTestApplication testApplication) {
    final String procDefId = "proc-status-" + nextStringId();
    createAndSaveRandomAgentInstance(
        testApplication,
        b -> b.processDefinitionId(procDefId).status(AgentInstanceStatus.THINKING));
    createAndSaveRandomAgentInstance(
        testApplication, b -> b.processDefinitionId(procDefId).status(AgentInstanceStatus.IDLE));
    createAndSaveRandomAgentInstance(
        testApplication, b -> b.processDefinitionId(procDefId).status(AgentInstanceStatus.IDLE));

    final var result =
        search(
            testApplication,
            new AgentInstanceFilter.Builder()
                .processDefinitionIds(procDefId)
                .statuses(AgentInstanceStatus.IDLE.name())
                .build());

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).allMatch(e -> e.status() == AgentInstanceStatus.IDLE);
  }

  @TestTemplate
  public void shouldFilterByTenantId(final CamundaRdbmsTestApplication testApplication) {
    final String tenantId = "tenant-" + nextStringId();
    createAndSaveRandomAgentInstance(testApplication, b -> b.tenantId(tenantId));
    createAndSaveRandomAgentInstance(testApplication, b -> b.tenantId(tenantId));
    createAndSaveRandomAgentInstance(testApplication, b -> b.tenantId("<other>"));

    final var result =
        search(testApplication, new AgentInstanceFilter.Builder().tenantIds(tenantId).build());

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).allMatch(e -> tenantId.equals(e.tenantId()));
  }

  @TestTemplate
  public void shouldFilterByElementId(final CamundaRdbmsTestApplication testApplication) {
    final String elementId = "Task_specificElement";
    final var model =
        createAndSaveRandomAgentInstance(testApplication, b -> b.elementId(elementId));
    createAndSaveRandomAgentInstance(testApplication, b -> b);

    final var result =
        search(testApplication, new AgentInstanceFilter.Builder().elementIds(elementId).build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().elementId()).isEqualTo(elementId);
  }

  @TestTemplate
  public void shouldFilterByRootProcessInstanceKey(
      final CamundaRdbmsTestApplication testApplication) {
    final long rootKey = nextKey();
    final var model =
        createAndSaveRandomAgentInstance(testApplication, b -> b.rootProcessInstanceKey(rootKey));
    createAndSaveRandomAgentInstance(testApplication, b -> b);

    final var result =
        search(
            testApplication,
            new AgentInstanceFilter.Builder().rootProcessInstanceKeys(rootKey).build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().agentInstanceKey()).isEqualTo(model.agentInstanceKey());
    assertThat(result.items().getFirst().rootProcessInstanceKey()).isEqualTo(rootKey);
  }

  @TestTemplate
  public void shouldFilterByElementInstanceKey(final CamundaRdbmsTestApplication testApplication) {
    final long elementInstanceKey = nextKey();
    final var model =
        createAndSaveRandomAgentInstance(
            testApplication, b -> b.elementInstanceKeys(List.of(elementInstanceKey)));
    createAndSaveRandomAgentInstance(testApplication, b -> b);

    final var result =
        search(
            testApplication,
            new AgentInstanceFilter.Builder().elementInstanceKeys(elementInstanceKey).build());

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().getFirst().agentInstanceKey()).isEqualTo(model.agentInstanceKey());
    assertThat(result.items().getFirst().elementInstanceKeys()).contains(elementInstanceKey);
  }

  private SearchQueryResult<AgentInstanceEntity> search(
      final CamundaRdbmsTestApplication testApplication, final AgentInstanceFilter filter) {
    return testApplication
        .getRdbmsService()
        .getAgentInstanceDbReader()
        .search(
            new AgentInstanceQuery(
                filter, AgentInstanceSort.of(b -> b), SearchQueryPage.of(b -> b)),
            ResourceAccessChecks.disabled());
  }
}
