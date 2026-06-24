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
import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel;
import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel.ClusterVariableDbModelBuilder;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ClusterVariableScope;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ClusterVariableFixtures extends CommonFixtures {

  private ClusterVariableFixtures() {}

  public static ClusterVariableDbModel createAndSaveRandomTenantClusterVariablesAndReturnOne(
      final CamundaRdbmsTestApplication testApplication) {
    return createAndSaveRandomsTenantClusterVariables(testApplication.getRdbmsService(), b -> b)
        .getLast();
  }

  public static ClusterVariableDbModel createAndSaveRandomGlobalClusterVariablesAndReturnOne(
      final CamundaRdbmsTestApplication testApplication) {
    return createAndSaveRandomsGlobalClusterVariables(testApplication.getRdbmsService(), b -> b)
        .getLast();
  }

  public static void createAndSaveRandomsGlobalClusterVariablesWithFixed(
      final RdbmsService rdbmsService, final String value) {
    createAndSaveRandomsGlobalClusterVariables(rdbmsService, b -> b.value(value));
  }

  public static ClusterVariableDbModel createRandomTenantClusterVariable(final String value) {
    return createRandomizedTenantClusterVariable(b -> b.value(value));
  }

  public static ClusterVariableDbModel createRandomGlobalClusterVariable(final String value) {
    return createRandomizedGlobalClusterVariable(b -> b.value(value));
  }

  public static void createAndSaveVariables(
      final RdbmsService rdbmsService, final ClusterVariableDbModel clusterVariableDbModel) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(0L);
    rdbmsWriters.getClusterVariableWriter().create(clusterVariableDbModel);
    rdbmsWriters.flush();
  }

  public static void createAndSaveRandomsTenantClusterVariablesWithFixedTenantId(
      final RdbmsService rdbmsService, final String tenantId) {
    createAndSaveRandomsTenantClusterVariablesWithFixedTenantId(rdbmsService, 20, tenantId);
  }

  public static void createAndSaveRandomsTenantClusterVariablesWithFixedTenantId(
      final RdbmsService rdbmsService, final int numberOfInstances, final String tenantId) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(0L);

    for (int i = 0; i < numberOfInstances; i++) {
      final ClusterVariableDbModel randomized =
          createRandomizedTenantClusterVariable(b -> b.tenantId(tenantId));
      rdbmsWriters.getClusterVariableWriter().create(randomized);
    }

    rdbmsWriters.flush();
  }

  public static void createAndSaveRandomsTenantClusterVariablesWithFixedTenantAndValue(
      final RdbmsService rdbmsService, final String tenantId, final String value) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(0L);

    for (int i = 0; i < 20; i++) {
      final ClusterVariableDbModel randomized =
          createRandomizedTenantClusterVariable(b -> b.tenantId(tenantId).value(value));
      rdbmsWriters.getClusterVariableWriter().create(randomized);
    }

    rdbmsWriters.flush();
  }

  private static List<ClusterVariableDbModel> createAndSaveRandomsTenantClusterVariables(
      final RdbmsService rdbmsService,
      final Function<ClusterVariableDbModelBuilder, ClusterVariableDbModelBuilder>
          builderFunction) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(0L);

    final List<ClusterVariableDbModel> models = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      final ClusterVariableDbModel randomized =
          createRandomizedTenantClusterVariable(builderFunction);
      models.add(randomized);
      rdbmsWriters.getClusterVariableWriter().create(randomized);
    }

    rdbmsWriters.flush();

    return models;
  }

  private static List<ClusterVariableDbModel> createAndSaveRandomsGlobalClusterVariables(
      final RdbmsService rdbmsService,
      final Function<ClusterVariableDbModelBuilder, ClusterVariableDbModelBuilder>
          builderFunction) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(0L);

    final List<ClusterVariableDbModel> models = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      final ClusterVariableDbModel randomized =
          createRandomizedGlobalClusterVariable(builderFunction);
      models.add(randomized);
      rdbmsWriters.getClusterVariableWriter().create(randomized);
    }

    rdbmsWriters.flush();

    return models;
  }

  private static ClusterVariableDbModel createRandomizedTenantClusterVariable(
      final Function<ClusterVariableDbModelBuilder, ClusterVariableDbModelBuilder>
          builderFunction) {
    final var builder =
        new ClusterVariableDbModelBuilder()
            .name(generateRandomString("name-"))
            .scope(ClusterVariableScope.TENANT)
            .tenantId(generateRandomStringWithRandomTypes());

    if (RANDOM.nextInt(10) < 5) {
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

  private static ClusterVariableDbModel createRandomizedGlobalClusterVariable(
      final Function<ClusterVariableDbModelBuilder, ClusterVariableDbModelBuilder>
          builderFunction) {
    final var builder =
        new ClusterVariableDbModelBuilder()
            .name(generateRandomString("name-"))
            .scope(ClusterVariableScope.GLOBAL);

    if (RANDOM.nextInt(10) < 5) {
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
}
