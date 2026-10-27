/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.AgentDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.AgentDefinitionDbModel.AgentDefinitionDbModelBuilder;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AgentDefinitionEntity.AgentType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class AgentDefinitionFixtures extends CommonFixtures {

  private AgentDefinitionFixtures() {}

  public static AgentDefinitionDbModel createRandomAgentDefinition(
      final Function<AgentDefinitionDbModelBuilder, AgentDefinitionDbModelBuilder>
          builderFunction) {
    final var key = nextKey();
    final var builder =
        new AgentDefinitionDbModelBuilder()
            .agentDefinitionKey(key)
            .agentType(randomEnum(AgentType.class))
            .name("agent-" + nextStringId())
            .elementId("element-" + key)
            .processDefinitionId("process-" + nextStringKey())
            .processDefinitionKey(nextKey())
            .processDefinitionVersion(1)
            .processDefinitionVersionTag("v-" + nextStringId())
            .tenantId("tenant-" + key);
    return builderFunction.apply(builder).build();
  }

  public static List<AgentDefinitionDbModel> createAndSaveRandomAgentDefinitions(
      final CamundaRdbmsTestApplication testApplication,
      final int count,
      final Function<AgentDefinitionDbModelBuilder, AgentDefinitionDbModelBuilder>
          builderFunction) {
    final RdbmsWriters rdbmsWriters = testApplication.getRdbmsService().createWriter(0);
    final List<AgentDefinitionDbModel> models = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final AgentDefinitionDbModel model = createRandomAgentDefinition(builderFunction);
      models.add(model);
      rdbmsWriters.getAgentDefinitionWriter().create(model);
    }
    rdbmsWriters.flush();
    return models;
  }

  public static AgentDefinitionDbModel createAndSaveRandomAgentDefinition(
      final CamundaRdbmsTestApplication testApplication,
      final Function<AgentDefinitionDbModelBuilder, AgentDefinitionDbModelBuilder>
          builderFunction) {
    return createAndSaveRandomAgentDefinitions(testApplication, 1, builderFunction).getFirst();
  }
}
