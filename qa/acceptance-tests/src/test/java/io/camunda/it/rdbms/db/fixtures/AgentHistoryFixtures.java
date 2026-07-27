/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.AgentHistoryDbModel;
import io.camunda.db.rdbms.write.domain.AgentHistoryDbModel.Builder;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class AgentHistoryFixtures extends CommonFixtures {

  private AgentHistoryFixtures() {}

  public static AgentHistoryDbModel createRandomAgentHistoryItem(
      final Function<Builder, Builder> builderFunction) {
    final var key = nextKey();
    final var agentInstanceKey = nextKey();
    final var processInstanceKey = nextKey();
    final var builder =
        new Builder()
            .agentHistoryKey(key)
            .agentInstanceKey(agentInstanceKey)
            .elementInstanceKey(nextKey())
            .processInstanceKey(processInstanceKey)
            .rootProcessInstanceKey(processInstanceKey)
            .processDefinitionId("process-" + nextStringKey())
            .processDefinitionKey(nextKey())
            .tenantId("<default>")
            .partitionId(1)
            .jobKey(nextKey())
            .jobLease("lease-" + key)
            .loopIteration(1)
            .role(AgentInstanceHistoryRole.USER)
            .commitStatus(AgentInstanceHistoryCommitStatus.PENDING)
            .producedAt(OffsetDateTime.now())
            .inputTokens(100L)
            .outputTokens(50L)
            .durationMs(200L)
            .contentItems(List.of())
            .toolCallValues(List.of());
    return builderFunction.apply(builder).build();
  }

  public static List<AgentHistoryDbModel> createAndSaveRandomAgentHistoryItems(
      final CamundaRdbmsTestApplication testApplication,
      final int count,
      final Function<Builder, Builder> builderFunction) {
    final RdbmsWriters rdbmsWriters = testApplication.getRdbmsService().createWriter(0);
    final List<AgentHistoryDbModel> models = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final AgentHistoryDbModel model = createRandomAgentHistoryItem(builderFunction);
      models.add(model);
      rdbmsWriters.getAgentHistoryWriter().create(model);
    }
    rdbmsWriters.flush();
    return models;
  }

  public static AgentHistoryDbModel createAndSaveRandomAgentHistoryItem(
      final CamundaRdbmsTestApplication testApplication,
      final Function<Builder, Builder> builderFunction) {
    return createAndSaveRandomAgentHistoryItems(testApplication, 1, builderFunction).getFirst();
  }
}
