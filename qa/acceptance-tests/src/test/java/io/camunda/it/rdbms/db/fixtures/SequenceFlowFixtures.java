/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.SequenceFlowDbModel;
import io.camunda.db.rdbms.write.domain.SequenceFlowDbModel.Builder;
import java.util.List;
import java.util.function.Function;

public final class SequenceFlowFixtures extends CommonFixtures {

  private SequenceFlowFixtures() {}

  public static SequenceFlowDbModel createRandomized(
      final Function<Builder, Builder> builderFunction) {
    final long nextKey = nextKey();
    final var builder =
        new Builder()
            .flowNodeId("flowNode" + nextKey)
            .processInstanceKey(nextKey())
            .rootProcessInstanceKey(nextKey())
            .processDefinitionKey(nextKey())
            .processDefinitionId("process-definition-" + nextKey)
            .tenantId("tenant-" + nextKey)
            .partitionId(0)
            .historyCleanupDate(NOW);

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomSequenceFlows(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomSequenceFlows(rdbmsWriters, b -> b);
  }

  public static void createAndSaveRandomSequenceFlows(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriters
          .getSequenceFlowWriter()
          .create(SequenceFlowFixtures.createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }

  public static SequenceFlowDbModel createAndSaveRandomSequenceFlow(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {

    final SequenceFlowDbModel sequenceFlow = SequenceFlowFixtures.createRandomized(builderFunction);
    rdbmsWriters.getSequenceFlowWriter().create(sequenceFlow);

    rdbmsWriters.flush();

    return sequenceFlow;
  }

  public static void createAndSaveSequenceFlow(
      final RdbmsWriters rdbmsWriters, final SequenceFlowDbModel sequenceFlow) {
    createAndSaveSequenceFlows(rdbmsWriters, List.of(sequenceFlow));
  }

  public static void createAndSaveSequenceFlows(
      final RdbmsWriters rdbmsWriters, final List<SequenceFlowDbModel> sequenceFlowList) {
    for (final SequenceFlowDbModel sequenceFlow : sequenceFlowList) {
      rdbmsWriters.getSequenceFlowWriter().create(sequenceFlow);
    }
    rdbmsWriters.flush();
  }
}
