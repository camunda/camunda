/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel.VariableDbModelBuilder;
import java.util.List;
import java.util.function.Function;

public final class VariableFixtures extends CommonFixtures {

  private VariableFixtures() {}

  public static VariableDbModel createRandomized() {
    return createRandomized(b -> b);
  }

  public static VariableDbModel createRandomized(
      final Function<VariableDbModelBuilder, VariableDbModelBuilder> builderFunction) {
    final var builder =
        new VariableDbModelBuilder()
            .key(nextKey())
            .processInstanceKey(nextKey())
            .scopeKey(nextKey())
            .name("variable-name-" + RANDOM.nextInt(1000))
            .value("variable-value-" + RANDOM.nextInt(1000))
            .tenantId("tenant-" + RANDOM.nextInt(1000));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomVariables(final RdbmsService rdbmsService) {
    createAndSaveRandomVariables(rdbmsService, nextKey());
  }

  public static void createAndSaveRandomVariables(
      final RdbmsService rdbmsService,
      final Function<VariableDbModelBuilder, VariableDbModelBuilder> builderFunction) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(0L);
    for (int i = 0; i < 20; i++) {
      rdbmsWriter.getVariableWriter().create(VariableFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static void createAndSaveRandomVariables(
      final RdbmsService rdbmsService, final Long scopeKey) {
    createAndSaveRandomVariables(rdbmsService, b -> b.scopeKey(scopeKey));
  }

  public static void createAndSaveVariable(
      final RdbmsService rdbmsService, final VariableDbModel processInstance) {
    createAndSaveVariables(rdbmsService, List.of(processInstance));
  }

  public static void createAndSaveVariables(
      final RdbmsService rdbmsService, final List<VariableDbModel> processInstanceList) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(0L);
    for (final VariableDbModel processInstance : processInstanceList) {
      rdbmsWriter.getVariableWriter().create(processInstance);
    }
    rdbmsWriter.flush();
  }
}
