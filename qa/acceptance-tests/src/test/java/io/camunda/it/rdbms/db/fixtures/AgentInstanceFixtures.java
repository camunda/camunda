/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel.Builder;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class AgentInstanceFixtures extends CommonFixtures {

  private AgentInstanceFixtures() {}

  public static AgentInstanceDbModel createRandomAgentInstance(
      final Function<Builder, Builder> builderFunction) {
    final var key = nextKey();
    final var builder =
        new Builder()
            .agentInstanceKey(key)
            .elementId("element-" + key)
            .processInstanceKey(nextKey())
            .rootProcessInstanceKey(-1L)
            .processDefinitionId("process-" + nextStringKey())
            .processDefinitionKey(nextKey())
            .processDefinitionVersion(1)
            .tenantId("<default>")
            .partitionId(1)
            .status(AgentInstanceStatus.IDLE)
            .model("gpt-4o")
            .provider("openai")
            .systemPrompt("You are an assistant.")
            .maxTokens(10000L)
            .maxModelCalls(100)
            .maxToolCalls(50)
            .inputTokens(0L)
            .outputTokens(0L)
            .modelCalls(0)
            .toolCalls(0)
            .creationDate(OffsetDateTime.now())
            .lastUpdatedDate(OffsetDateTime.now())
            .elementInstanceKeys(List.of(nextKey()));
    return builderFunction.apply(builder).build();
  }

  public static List<AgentInstanceDbModel> createAndSaveRandomAgentInstances(
      final CamundaRdbmsTestApplication testApplication,
      final int count,
      final Function<Builder, Builder> builderFunction) {
    final RdbmsWriters rdbmsWriters = testApplication.getRdbmsService().createWriter(0);
    final List<AgentInstanceDbModel> models = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final AgentInstanceDbModel model = createRandomAgentInstance(builderFunction);
      models.add(model);
      rdbmsWriters.getAgentInstanceWriter().create(model);
    }
    rdbmsWriters.flush();
    return models;
  }

  public static AgentInstanceDbModel createAndSaveRandomAgentInstance(
      final CamundaRdbmsTestApplication testApplication,
      final Function<Builder, Builder> builderFunction) {
    return createAndSaveRandomAgentInstances(testApplication, 1, builderFunction).getFirst();
  }
}
