/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedInput;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedOutput;
import io.camunda.search.entities.DecisionInstanceEntity;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

public final class DecisionInstanceFixtures extends CommonFixtures {

  private DecisionInstanceFixtures() {}

  public static DecisionInstanceDbModel createRandomized(
      final Function<DecisionInstanceDbModel.Builder, DecisionInstanceDbModel.Builder>
          builderFunction) {
    final var decisionInstanceKey = nextKey();
    final var decisionInstanceId = decisionInstanceKey + "-1";
    final var builder =
        new DecisionInstanceDbModel.Builder()
            .decisionInstanceId(decisionInstanceId)
            .decisionInstanceKey(decisionInstanceKey)
            .processInstanceKey(nextKey())
            .processDefinitionKey(nextKey())
            .processDefinitionId("process-" + RANDOM.nextInt(10000))
            .flowNodeInstanceKey(nextKey())
            .flowNodeId("flow-node-" + RANDOM.nextInt(10000))
            .decisionRequirementsKey(nextKey())
            .decisionRequirementsId("requirements-" + RANDOM.nextInt(10000))
            .decisionDefinitionKey(nextKey())
            .decisionDefinitionId("decision-" + RANDOM.nextInt(1000))
            .evaluationDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .result("result-" + RANDOM.nextInt(10000))
            .evaluationFailure("failure-" + RANDOM.nextInt(10000))
            .decisionType(randomEnum(DecisionInstanceEntity.DecisionDefinitionType.class))
            .tenantId("tenant-" + RANDOM.nextInt(10000))
            .state(randomEnum(DecisionInstanceEntity.DecisionInstanceState.class))
            .evaluatedInputs(randomInputs(decisionInstanceId))
            .evaluatedOutputs(randomOutputs(decisionInstanceId));

    return builderFunction.apply(builder).build();
  }

  private static List<EvaluatedInput> randomInputs(final String id) {
    return IntStream.range(0, RANDOM.nextInt(5) + 5)
        .boxed()
        .map(i -> new EvaluatedInput(id, "input-" + i, "Input " + i, Integer.toString(i)))
        .toList();
  }

  private static List<EvaluatedOutput> randomOutputs(final String id) {
    return IntStream.range(0, RANDOM.nextInt(5) + 5)
        .boxed()
        .map(
            i ->
                new EvaluatedOutput(
                    id, "input-" + i, "Input " + i, Integer.toString(i), "rule-" + i, i))
        .toList();
  }

  public static void createAndSaveRandomDecisionInstances(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomDecisionInstances(rdbmsWriter, b -> b);
  }

  public static DecisionInstanceDbModel createAndSaveRandomDecisionInstance(
      final RdbmsWriter rdbmsWriter,
      final Function<DecisionInstanceDbModel.Builder, DecisionInstanceDbModel.Builder>
          builderFunction) {
    final var instance = createRandomized(builderFunction);
    rdbmsWriter.getDecisionInstanceWriter().create(instance);
    rdbmsWriter.flush();
    return instance;
  }

  public static void createAndSaveRandomDecisionInstances(
      final RdbmsWriter rdbmsWriter,
      final Function<DecisionInstanceDbModel.Builder, DecisionInstanceDbModel.Builder>
          builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getDecisionInstanceWriter()
          .create(DecisionInstanceFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static void createAndSaveDecisionInstance(
      final RdbmsWriter rdbmsWriter, final DecisionInstanceDbModel pecisionInstance) {
    createAndSaveDecisionInstances(rdbmsWriter, List.of(pecisionInstance));
  }

  public static void createAndSaveDecisionInstances(
      final RdbmsWriter rdbmsWriter, final List<DecisionInstanceDbModel> pecisionInstanceList) {
    for (final DecisionInstanceDbModel pecisionInstance : pecisionInstanceList) {
      rdbmsWriter.getDecisionInstanceWriter().create(pecisionInstance);
    }
    rdbmsWriter.flush();
  }
}
