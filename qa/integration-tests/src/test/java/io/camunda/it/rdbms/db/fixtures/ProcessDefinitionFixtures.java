/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import java.time.OffsetDateTime;
import java.util.Random;
import java.util.function.Function;

public final class ProcessDefinitionFixtures {

  private static final Random RANDOM = new Random(System.nanoTime());
  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private ProcessDefinitionFixtures() {}

  public static ProcessDefinitionDbModel createRandomized(
      final Function<ProcessDefinitionDbModelBuilder, ProcessDefinitionDbModelBuilder>
          builderFunction) {
    final var processDefinitionKey = RANDOM.nextLong(1000);
    final var version = RANDOM.nextInt(100);
    final var builder =
        new ProcessDefinitionDbModelBuilder()
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionId("process-" + processDefinitionKey)
            .name("Process " + processDefinitionKey)
            .version(version)
            .versionTag("Version " + version)
            .tenantId("tenant-" + RANDOM.nextInt(1000));

    return builderFunction.apply(builder).build();
  }
}
