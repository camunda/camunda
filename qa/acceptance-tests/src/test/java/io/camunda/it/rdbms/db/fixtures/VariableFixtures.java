/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel.VariableDbModelBuilder;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class VariableFixtures extends CommonFixtures {

  private VariableFixtures() {}

  public static VariableDbModel createRandomized() {
    return createRandomized(b -> b);
  }

  public static VariableDbModel createRandomized(
      final Function<VariableDbModelBuilder, VariableDbModelBuilder> builderFunction) {
    final var key = nextKey();
    final var builder =
        new VariableDbModelBuilder()
            .variableKey(key)
            .processInstanceKey(nextKey())
            .rootProcessInstanceKey(nextKey())
            .processDefinitionId("process-definition-id-" + key)
            .tenantId("tenant-id-" + key)
            .scopeKey(nextKey())
            .name(
                "variable-name-"
                    + RANDOM.nextInt(10)) // We sometimes want variables with the same name
            .tenantId("tenant-" + key);

    if (RANDOM.nextInt(10) != 5) {
      builder.value(generateRandomStringWithRandomTypes());
    } else {
      // Sometimes we save a json
      try {
        final List<String> randomStrings =
            IntStream.range(0, 100)
                .mapToObj(i -> generateRandomStringWithRandomTypes())
                .collect(Collectors.toList());
        builder.value(new ObjectMapper().writeValueAsString(randomStrings));
      } catch (final JsonProcessingException ignored) {
        System.out.println("Could not serialize object to json");
      }
    }

    return builderFunction.apply(builder).build();
  }

  public static List<VariableDbModel> createAndSaveRandomVariablesWithFixedName(
      final RdbmsService rdbmsService) {
    final String variableName = "var-name-" + nextStringId();
    return createAndSaveRandomVariables(rdbmsService, variableName);
  }

  public static List<VariableDbModel> createAndSaveRandomVariables(
      final RdbmsService rdbmsService, final String variableName) {
    return createAndSaveRandomVariables(rdbmsService, b -> b.name(variableName));
  }

  public static List<VariableDbModel> createAndSaveRandomVariables(
      final RdbmsService rdbmsService,
      final Function<VariableDbModelBuilder, VariableDbModelBuilder> builderFunction) {
    return createAndSaveRandomVariables(rdbmsService, 20, builderFunction);
  }

  public static List<VariableDbModel> createAndSaveRandomVariables(
      final RdbmsService rdbmsService,
      final int numberOfInstances,
      final Function<VariableDbModelBuilder, VariableDbModelBuilder> builderFunction) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(0L);

    final List<VariableDbModel> models = new ArrayList<>();
    for (int i = 0; i < numberOfInstances; i++) {
      final VariableDbModel randomized = createRandomized(builderFunction);
      models.add(randomized);
      rdbmsWriters.getVariableWriter().create(randomized);
    }

    rdbmsWriters.flush();

    return models;
  }

  public static VariableDbModel createAndSaveVariable(
      final RdbmsService rdbmsService,
      final Function<VariableDbModelBuilder, VariableDbModelBuilder> builderFunction) {
    final var randomized = createRandomized(builderFunction);
    createAndSaveVariables(rdbmsService, List.of(randomized));
    return randomized;
  }

  public static void createAndSaveVariable(
      final RdbmsService rdbmsService, final VariableDbModel processInstance) {
    createAndSaveVariables(rdbmsService, List.of(processInstance));
  }

  public static void createAndSaveVariables(
      final RdbmsService rdbmsService, final List<VariableDbModel> variableList) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(0L);
    for (final VariableDbModel variable : variableList) {
      rdbmsWriters.getVariableWriter().create(variable);
    }
    rdbmsWriters.flush();
  }

  public static void prepareRandomVariables(final CamundaRdbmsTestApplication testApplication) {
    VariableFixtures.createAndSaveRandomVariables(testApplication.getRdbmsService(), b -> b);
  }

  public static VariableDbModel prepareRandomVariablesAndReturnOne(
      final CamundaRdbmsTestApplication testApplication) {
    return VariableFixtures.createAndSaveRandomVariables(testApplication.getRdbmsService(), b -> b)
        .getLast();
  }

  public static VariableDbModel prepareRandomVariablesAndReturnOne(
      final CamundaRdbmsTestApplication testApplication,
      final String variableName,
      final String valueForOne) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // 20 variables
    createAndSaveRandomVariables(rdbmsService, variableName);

    // 1 with our value
    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.name(variableName).value(valueForOne));
    createAndSaveVariable(rdbmsService, randomizedVariable);

    return randomizedVariable;
  }
}
