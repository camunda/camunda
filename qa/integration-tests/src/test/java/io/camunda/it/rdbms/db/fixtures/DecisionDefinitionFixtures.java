/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
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
    final var builder =
        new DecisionDefinitionDbModelBuilder()
            .decisionDefinitionKey(decisionDefinitionKey)
            .decisionDefinitionId("process-" + decisionDefinitionKey)
            .name("Process " + decisionDefinitionKey)
            .version(version)
            .decisionRequirementsKey(decisionRequirementsKey)
            .decisionRequirementsId("decision-requirements-" + decisionRequirementsKey)
            .tenantId("tenant-" + RANDOM.nextInt(1000));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomDecisionDefinitions(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomDecisionDefinitions(
        rdbmsWriter, b -> b.decisionDefinitionId(nextStringId()));
  }

  public static DecisionDefinitionDbModel createAndSaveRandomDecisionDefinition(
      final RdbmsWriter rdbmsWriter,
      final Function<DecisionDefinitionDbModelBuilder, DecisionDefinitionDbModelBuilder>
          builderFunction) {
    final var definition = DecisionDefinitionFixtures.createRandomized(builderFunction);
    rdbmsWriter.getDecisionDefinitionWriter().create(definition);
    rdbmsWriter.flush();
    return definition;
  }

  public static void createAndSaveRandomDecisionDefinitions(
      final RdbmsWriter rdbmsWriter,
      final Function<DecisionDefinitionDbModelBuilder, DecisionDefinitionDbModelBuilder>
          builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getDecisionDefinitionWriter()
          .create(DecisionDefinitionFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static DecisionDefinitionDbModel createAndSaveDecisionDefinition(
      final RdbmsWriter rdbmsWriter,
      final Function<DecisionDefinitionDbModelBuilder, DecisionDefinitionDbModelBuilder>
          builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveDecisionDefinitions(rdbmsWriter, List.of(definition));
    return definition;
  }

  public static void createAndSaveDecisionDefinition(
      final RdbmsWriter rdbmsWriter, final DecisionDefinitionDbModel decisionDefinition) {
    createAndSaveDecisionDefinitions(rdbmsWriter, List.of(decisionDefinition));
  }

  public static void createAndSaveDecisionDefinitions(
      final RdbmsWriter rdbmsWriter, final List<DecisionDefinitionDbModel> decisionDefinitionList) {
    for (final DecisionDefinitionDbModel decisionDefinition : decisionDefinitionList) {
      rdbmsWriter.getDecisionDefinitionWriter().create(decisionDefinition);
    }
    rdbmsWriter.flush();
  }
}
