/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
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
            .rootProcessInstanceKey(nextKey())
            .processDefinitionKey(nextKey())
            .processDefinitionId("process-" + decisionInstanceKey)
            .flowNodeInstanceKey(nextKey())
            .flowNodeId("flow-node-" + decisionInstanceKey)
            .decisionRequirementsKey(nextKey())
            .decisionRequirementsId("requirements-" + decisionInstanceKey)
            .decisionDefinitionKey(nextKey())
            .decisionDefinitionId("decision-" + decisionInstanceKey)
            .rootDecisionDefinitionKey(nextKey())
            .evaluationDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .result("result-" + RANDOM.nextInt(10000))
            .evaluationFailure("failure-" + RANDOM.nextInt(10000))
            .evaluationFailureMessage("failure-message" + RANDOM.nextInt(10000))
            .decisionType(randomEnum(DecisionInstanceEntity.DecisionDefinitionType.class))
            .tenantId("tenant-" + decisionInstanceKey)
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

  public static void createAndSaveRandomDecisionInstances(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomDecisionInstances(rdbmsWriters, b -> b);
  }

  public static DecisionInstanceDbModel createAndSaveRandomDecisionInstance(
      final RdbmsWriters rdbmsWriters,
      final Function<DecisionInstanceDbModel.Builder, DecisionInstanceDbModel.Builder>
          builderFunction) {
    final var instance = createRandomized(builderFunction);
    rdbmsWriters.getDecisionInstanceWriter().create(instance);
    rdbmsWriters.flush();
    return instance;
  }

  public static void createAndSaveRandomDecisionInstances(
      final RdbmsWriters rdbmsWriters,
      final Function<DecisionInstanceDbModel.Builder, DecisionInstanceDbModel.Builder>
          builderFunction) {
    createAndSaveRandomDecisionInstances(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomDecisionInstances(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<DecisionInstanceDbModel.Builder, DecisionInstanceDbModel.Builder>
          builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters
          .getDecisionInstanceWriter()
          .create(DecisionInstanceFixtures.createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }

  public static void createAndSaveDecisionInstance(
      final RdbmsWriters rdbmsWriters, final DecisionInstanceDbModel pecisionInstance) {
    createAndSaveDecisionInstances(rdbmsWriters, List.of(pecisionInstance));
  }

  public static void createAndSaveDecisionInstances(
      final RdbmsWriters rdbmsWriters, final List<DecisionInstanceDbModel> pecisionInstanceList) {
    for (final DecisionInstanceDbModel pecisionInstance : pecisionInstanceList) {
      rdbmsWriters.getDecisionInstanceWriter().create(pecisionInstance);
    }
    rdbmsWriters.flush();
  }
}
