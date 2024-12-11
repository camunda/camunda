/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
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
            .processDefinitionKey(nextKey())
            .processDefinitionId("process-" + generateRandomString(20))
            .flowNodeId("flowNode-" + generateRandomString(20))
            .startDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .endDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .treePath(nextStringId())
            .incidentKey(nextKey())
            .state(randomEnum(FlowNodeState.class))
            .type(randomEnum(FlowNodeType.class))
            .tenantId("tenant-" + generateRandomString(20));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomFlowNodeInstances(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomFlowNodeInstances(rdbmsWriter, b -> b);
  }

  public static void createAndSaveRandomFlowNodeInstances(
      final RdbmsWriter rdbmsWriter,
      final Function<FlowNodeInstanceDbModelBuilder, FlowNodeInstanceDbModelBuilder>
          builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getFlowNodeInstanceWriter()
          .create(FlowNodeInstanceFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static FlowNodeInstanceDbModel createAndSaveFlowNodeInstance(
      final RdbmsWriter rdbmsWriter) {
    final var instance = FlowNodeInstanceFixtures.createRandomized(b -> b);
    createAndSaveFlowNodeInstances(rdbmsWriter, List.of(instance));
    return instance;
  }

  public static FlowNodeInstanceDbModel createAndSaveFlowNodeInstance(
      final RdbmsWriter rdbmsWriter,
      final Function<FlowNodeInstanceDbModelBuilder, FlowNodeInstanceDbModelBuilder>
          builderFunction) {
    final var instance = FlowNodeInstanceFixtures.createRandomized(builderFunction);
    createAndSaveFlowNodeInstances(rdbmsWriter, List.of(instance));
    return instance;
  }

  public static void createAndSaveFlowNodeInstance(
      final RdbmsWriter rdbmsWriter, final FlowNodeInstanceDbModel processInstance) {
    createAndSaveFlowNodeInstances(rdbmsWriter, List.of(processInstance));
  }

  public static void createAndSaveFlowNodeInstances(
      final RdbmsWriter rdbmsWriter, final List<FlowNodeInstanceDbModel> processInstanceList) {
    for (final FlowNodeInstanceDbModel processInstance : processInstanceList) {
      rdbmsWriter.getFlowNodeInstanceWriter().create(processInstance);
    }
    rdbmsWriter.flush();
  }
}
