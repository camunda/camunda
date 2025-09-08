/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
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
            .processDefinitionKey(nextKey())
            .processDefinitionId("process-definition-" + nextKey)
            .tenantId("tenant-" + nextKey)
            .partitionId(0)
            .historyCleanupDate(NOW);

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomSequenceFlows(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomSequenceFlows(rdbmsWriter, b -> b);
  }

  public static void createAndSaveRandomSequenceFlows(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getSequenceFlowWriter()
          .create(SequenceFlowFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static SequenceFlowDbModel createAndSaveRandomSequenceFlow(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {

    final SequenceFlowDbModel sequenceFlow = SequenceFlowFixtures.createRandomized(builderFunction);
    rdbmsWriter.getSequenceFlowWriter().create(sequenceFlow);

    rdbmsWriter.flush();

    return sequenceFlow;
  }

  public static void createAndSaveSequenceFlow(
      final RdbmsWriter rdbmsWriter, final SequenceFlowDbModel sequenceFlow) {
    createAndSaveSequenceFlows(rdbmsWriter, List.of(sequenceFlow));
  }

  public static void createAndSaveSequenceFlows(
      final RdbmsWriter rdbmsWriter, final List<SequenceFlowDbModel> sequenceFlowList) {
    for (final SequenceFlowDbModel sequenceFlow : sequenceFlowList) {
      rdbmsWriter.getSequenceFlowWriter().create(sequenceFlow);
    }
    rdbmsWriter.flush();
  }
}
