/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.historycleanup;

import static io.camunda.it.rdbms.db.fixtures.AgentHistoryFixtures.createAndSaveRandomAgentHistoryItem;
import static io.camunda.it.rdbms.db.fixtures.AgentInstanceFixtures.createAndSaveRandomAgentInstance;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveRandomRootProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.filter.AgentInstanceHistoryFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AgentInstanceHistoryQuery;
import io.camunda.search.sort.AgentInstanceHistorySort;
import io.camunda.security.core.authz.ResourceAccessChecks;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AgentHistoryCleanupIT {

  @TestTemplate
  public void shouldDeleteAgentInstancesAndAgentHistoryOfExpiredRootProcessInstance(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var writers = rdbmsService.createWriter(0);
    final var historyCleanupService =
        new HistoryCleanupService(
            RdbmsWriterConfig.builder().build(), writers, rdbmsService.getProcessInstanceReader());

    final var expiredRoot =
        createAndSaveRandomRootProcessInstance(
            writers,
            b -> b.partitionId(0).historyCleanupDate(OffsetDateTime.now().minusSeconds(1)));
    final var retainedRoot = createAndSaveRandomRootProcessInstance(writers, b -> b.partitionId(0));

    final var expiredAgentInstance =
        createAndSaveRandomAgentInstance(
            testApplication,
            b ->
                b.partitionId(0)
                    .processInstanceKey(expiredRoot.processInstanceKey())
                    .rootProcessInstanceKey(expiredRoot.rootProcessInstanceKey()));
    createAndSaveRandomAgentHistoryItem(
        testApplication,
        b ->
            b.partitionId(0)
                .agentInstanceKey(expiredAgentInstance.agentInstanceKey())
                .processInstanceKey(expiredRoot.processInstanceKey())
                .rootProcessInstanceKey(expiredRoot.rootProcessInstanceKey()));
    final var retainedAgentInstance =
        createAndSaveRandomAgentInstance(
            testApplication,
            b ->
                b.partitionId(0)
                    .processInstanceKey(retainedRoot.processInstanceKey())
                    .rootProcessInstanceKey(retainedRoot.rootProcessInstanceKey()));
    createAndSaveRandomAgentHistoryItem(
        testApplication,
        b ->
            b.partitionId(0)
                .agentInstanceKey(retainedAgentInstance.agentInstanceKey())
                .processInstanceKey(retainedRoot.processInstanceKey())
                .rootProcessInstanceKey(retainedRoot.rootProcessInstanceKey()));

    assertThat(agentInstanceExists(rdbmsService, expiredAgentInstance.agentInstanceKey())).isTrue();
    assertThat(agentHistoryCount(rdbmsService, expiredAgentInstance.agentInstanceKey()))
        .isEqualTo(1);

    // when
    historyCleanupService.cleanupHistory(0, OffsetDateTime.now());

    // then
    assertThat(agentInstanceExists(rdbmsService, expiredAgentInstance.agentInstanceKey()))
        .as("agent instance %d exists", expiredAgentInstance.agentInstanceKey())
        .isFalse();
    assertThat(agentHistoryCount(rdbmsService, expiredAgentInstance.agentInstanceKey()))
        .as("agent history rows of agent instance %d", expiredAgentInstance.agentInstanceKey())
        .isZero();
    assertThat(agentInstanceExists(rdbmsService, retainedAgentInstance.agentInstanceKey()))
        .isTrue();
    assertThat(agentHistoryCount(rdbmsService, retainedAgentInstance.agentInstanceKey()))
        .isEqualTo(1);
  }

  private static boolean agentInstanceExists(
      final RdbmsService rdbmsService, final long agentInstanceKey) {
    return rdbmsService
            .getAgentInstanceDbReader()
            .getByKey(agentInstanceKey, ResourceAccessChecks.disabled())
        != null;
  }

  private static long agentHistoryCount(
      final RdbmsService rdbmsService, final long agentInstanceKey) {
    return rdbmsService
        .getAgentHistoryDbReader()
        .search(
            new AgentInstanceHistoryQuery(
                new AgentInstanceHistoryFilter.Builder()
                    .agentInstanceKeys(agentInstanceKey)
                    .build(),
                AgentInstanceHistorySort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))),
            ResourceAccessChecks.disabled())
        .total();
  }
}
