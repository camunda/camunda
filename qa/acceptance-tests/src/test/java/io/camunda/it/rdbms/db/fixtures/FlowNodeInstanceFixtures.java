/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

public final class FlowNodeInstanceFixtures extends CommonFixtures {

  private FlowNodeInstanceFixtures() {}

  public static FlowNodeInstanceDbModel createRandomized(
      final Function<FlowNodeInstanceDbModelBuilder, FlowNodeInstanceDbModelBuilder>
          builderFunction) {
    final var builder =
        new FlowNodeInstanceDbModelBuilder()
            .flowNodeInstanceKey(nextKey())
            .processInstanceKey(nextKey())
            .rootProcessInstanceKey(nextKey())
            .processDefinitionKey(nextKey())
            .processDefinitionId("process-" + generateRandomString(20))
            .flowNodeId("flowNode-" + generateRandomString(20))
            .flowNodeScopeKey(nextKey())
            .startDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .endDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .treePath(nextStringId())
            .incidentKey(nextKey())
            .state(randomEnum(FlowNodeState.class))
            .type(randomEnum(FlowNodeType.class))
            .tenantId("tenant-" + generateRandomString(20));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomFlowNodeInstances(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomFlowNodeInstances(rdbmsWriters, b -> b);
  }

  public static void createAndSaveRandomFlowNodeInstances(
      final RdbmsWriters rdbmsWriters,
      final Function<FlowNodeInstanceDbModelBuilder, FlowNodeInstanceDbModelBuilder>
          builderFunction) {
    createAndSaveRandomFlowNodeInstances(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomFlowNodeInstances(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<FlowNodeInstanceDbModelBuilder, FlowNodeInstanceDbModelBuilder>
          builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters
          .getFlowNodeInstanceWriter()
          .create(FlowNodeInstanceFixtures.createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }

  public static FlowNodeInstanceDbModel createAndSaveRandomFlowNodeInstance(
      final RdbmsWriters rdbmsWriters) {
    final var instance = FlowNodeInstanceFixtures.createRandomized(b -> b);
    createAndSaveFlowNodeInstances(rdbmsWriters, List.of(instance));
    return instance;
  }

  public static FlowNodeInstanceDbModel createAndSaveRandomFlowNodeInstance(
      final RdbmsWriters rdbmsWriters,
      final Function<FlowNodeInstanceDbModelBuilder, FlowNodeInstanceDbModelBuilder>
          builderFunction) {
    final var instance = FlowNodeInstanceFixtures.createRandomized(builderFunction);
    createAndSaveFlowNodeInstances(rdbmsWriters, List.of(instance));
    return instance;
  }

  public static void createAndSaveRandomFlowNodeInstance(
      final RdbmsWriters rdbmsWriters, final FlowNodeInstanceDbModel processInstance) {
    createAndSaveFlowNodeInstances(rdbmsWriters, List.of(processInstance));
  }

  public static void createAndSaveFlowNodeInstances(
      final RdbmsWriters rdbmsWriters, final List<FlowNodeInstanceDbModel> processInstanceList) {
    for (final FlowNodeInstanceDbModel processInstance : processInstanceList) {
      rdbmsWriters.getFlowNodeInstanceWriter().create(processInstance);
    }
    rdbmsWriters.flush();
  }
}
