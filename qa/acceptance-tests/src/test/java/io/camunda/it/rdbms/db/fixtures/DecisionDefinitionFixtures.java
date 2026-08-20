/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder;
import java.util.List;
import java.util.function.Function;

public final class DecisionDefinitionFixtures extends CommonFixtures {

  private DecisionDefinitionFixtures() {}

  public static DecisionDefinitionDbModel createRandomized(
      final Function<DecisionDefinitionDbModelBuilder, DecisionDefinitionDbModelBuilder>
          builderFunction) {
    final var decisionDefinitionKey = nextKey();
    final var decisionRequirementsKey = nextKey();
    final var version = RANDOM.nextInt(1000);
    final var decisionRequirementsVersion = RANDOM.nextInt(1000);
    final var builder =
        new DecisionDefinitionDbModelBuilder()
            .decisionDefinitionKey(decisionDefinitionKey)
            .decisionDefinitionId("process-" + decisionDefinitionKey)
            .name("Process " + decisionDefinitionKey)
            .version(version)
            .decisionRequirementsKey(decisionRequirementsKey)
            .decisionRequirementsId("decision-requirements-" + decisionRequirementsKey)
            .decisionRequirementsName("decision-requirements-name-" + decisionRequirementsKey)
            .decisionRequirementsVersion(decisionRequirementsVersion)
            .tenantId("tenant-" + decisionDefinitionKey);

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomDecisionDefinitions(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomDecisionDefinitions(
        rdbmsWriters, b -> b.decisionDefinitionId(nextStringId()));
  }

  public static DecisionDefinitionDbModel createAndSaveRandomDecisionDefinition(
      final RdbmsWriters rdbmsWriters,
      final Function<DecisionDefinitionDbModelBuilder, DecisionDefinitionDbModelBuilder>
          builderFunction) {
    final var definition = DecisionDefinitionFixtures.createRandomized(builderFunction);
    rdbmsWriters.getDecisionDefinitionWriter().create(definition);
    rdbmsWriters.flush();
    return definition;
  }

  public static void createAndSaveRandomDecisionDefinitions(
      final RdbmsWriters rdbmsWriters,
      final Function<DecisionDefinitionDbModelBuilder, DecisionDefinitionDbModelBuilder>
          builderFunction) {
    createAndSaveRandomDecisionDefinitions(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomDecisionDefinitions(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<DecisionDefinitionDbModelBuilder, DecisionDefinitionDbModelBuilder>
          builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters
          .getDecisionDefinitionWriter()
          .create(DecisionDefinitionFixtures.createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }

  public static DecisionDefinitionDbModel createAndSaveDecisionDefinition(
      final RdbmsWriters rdbmsWriters,
      final Function<DecisionDefinitionDbModelBuilder, DecisionDefinitionDbModelBuilder>
          builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveDecisionDefinitions(rdbmsWriters, List.of(definition));
    return definition;
  }

  public static void createAndSaveDecisionDefinition(
      final RdbmsWriters rdbmsWriters, final DecisionDefinitionDbModel decisionDefinition) {
    createAndSaveDecisionDefinitions(rdbmsWriters, List.of(decisionDefinition));
  }

  public static void createAndSaveDecisionDefinitions(
      final RdbmsWriters rdbmsWriters,
      final List<DecisionDefinitionDbModel> decisionDefinitionList) {
    for (final DecisionDefinitionDbModel decisionDefinition : decisionDefinitionList) {
      rdbmsWriters.getDecisionDefinitionWriter().create(decisionDefinition);
    }
    rdbmsWriters.flush();
  }
}
