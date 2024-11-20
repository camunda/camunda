/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel.Builder;
import java.util.List;
import java.util.function.Function;

public final class DecisionRequirementsFixtures extends CommonFixtures {

  private DecisionRequirementsFixtures() {}

  public static DecisionRequirementsDbModel createRandomized(
      final Function<Builder, Builder> builderFunction) {
    final var decisionRequirementsKey = nextKey();
    final var version = RANDOM.nextInt(1000);
    final var builder =
        new Builder()
            .decisionRequirementsKey(decisionRequirementsKey)
            .decisionRequirementsId("requirement-" + decisionRequirementsKey)
            .name("requirement " + decisionRequirementsKey)
            .version(version)
            .tenantId("tenant-" + RANDOM.nextInt(1000))
            .resourceName("requirement-" + decisionRequirementsKey + ".xml")
            .xml("<xml>" + decisionRequirementsKey + "</xml>");

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomDecisionRequirements(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomDecisionRequirements(
        rdbmsWriter, b -> b.decisionRequirementsId(nextStringId()));
  }

  public static void createAndSaveRandomDecisionRequirements(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getDecisionRequirementsWriter()
          .create(DecisionRequirementsFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static DecisionRequirementsDbModel createAndSaveDecisionRequirement(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveDecisionRequirements(rdbmsWriter, List.of(definition));
    return definition;
  }

  public static void createAndSaveDecisionRequirement(
      final RdbmsWriter rdbmsWriter, final DecisionRequirementsDbModel decisionRequirements) {
    createAndSaveDecisionRequirements(rdbmsWriter, List.of(decisionRequirements));
  }

  public static void createAndSaveDecisionRequirements(
      final RdbmsWriter rdbmsWriter,
      final List<DecisionRequirementsDbModel> decisionRequirementsList) {
    for (final DecisionRequirementsDbModel decisionRequirements : decisionRequirementsList) {
      rdbmsWriter.getDecisionRequirementsWriter().create(decisionRequirements);
    }
    rdbmsWriter.flush();
  }
}
