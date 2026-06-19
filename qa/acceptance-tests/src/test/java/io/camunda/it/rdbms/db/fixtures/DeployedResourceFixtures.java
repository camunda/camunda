/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.DeployedResourceDbModel;
import io.camunda.db.rdbms.write.domain.DeployedResourceDbModel.DeployedResourceDbModelBuilder;
import java.util.List;
import java.util.function.Function;

public final class DeployedResourceFixtures extends CommonFixtures {

  private DeployedResourceFixtures() {}

  public static DeployedResourceDbModel createRandomized(
      final Function<DeployedResourceDbModelBuilder, DeployedResourceDbModelBuilder>
          builderFunction) {
    final var builder =
        new DeployedResourceDbModelBuilder()
            .resourceKey(nextKey())
            .resourceId("resource-id-" + generateRandomString(20))
            .resourceName("resource-" + generateRandomString(20) + ".bpmn")
            .resourceType("PROCESS")
            .version(RANDOM.nextInt(1, 100))
            .versionTag("v" + generateRandomString(5))
            .deploymentKey(nextKey())
            .tenantId("tenant-" + generateRandomString(20))
            .resourceContent(generateRandomString(50).getBytes());
    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomDeployedResources(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomDeployedResources(rdbmsWriters, b -> b);
  }

  public static void createAndSaveRandomDeployedResources(
      final RdbmsWriters rdbmsWriters,
      final Function<DeployedResourceDbModelBuilder, DeployedResourceDbModelBuilder>
          builderFunction) {
    createAndSaveRandomDeployedResources(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomDeployedResources(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<DeployedResourceDbModelBuilder, DeployedResourceDbModelBuilder>
          builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters.getResourceWriter().create(createRandomized(builderFunction));
    }
    rdbmsWriters.flush();
  }

  public static DeployedResourceDbModel createAndSaveDeployedResource(
      final RdbmsWriters rdbmsWriters,
      final Function<DeployedResourceDbModelBuilder, DeployedResourceDbModelBuilder>
          builderFunction) {
    final DeployedResourceDbModel model = createRandomized(builderFunction);
    createAndSaveDeployedResources(rdbmsWriters, List.of(model));
    return model;
  }

  public static void createAndSaveDeployedResource(
      final RdbmsWriters rdbmsWriters, final DeployedResourceDbModel resource) {
    createAndSaveDeployedResources(rdbmsWriters, List.of(resource));
  }

  public static void createAndSaveDeployedResources(
      final RdbmsWriters rdbmsWriters, final List<DeployedResourceDbModel> resources) {
    for (final DeployedResourceDbModel resource : resources) {
      rdbmsWriters.getResourceWriter().create(resource);
    }
    rdbmsWriters.flush();
  }
}
