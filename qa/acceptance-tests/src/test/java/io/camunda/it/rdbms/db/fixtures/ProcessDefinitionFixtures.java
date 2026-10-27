/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder;
import java.util.List;
import java.util.function.Function;

public final class ProcessDefinitionFixtures extends CommonFixtures {

  private ProcessDefinitionFixtures() {}

  public static ProcessDefinitionDbModel createRandomized(
      final Function<ProcessDefinitionDbModelBuilder, ProcessDefinitionDbModelBuilder>
          builderFunction) {
    final var processDefinitionKey = nextKey();
    final var version = RANDOM.nextInt(1000);
    final var builder =
        new ProcessDefinitionDbModelBuilder()
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionId("process-" + processDefinitionKey)
            .name("Process " + processDefinitionKey)
            .resourceName("process_" + processDefinitionKey + ".bpmn")
            .version(version)
            .versionTag("Version " + version)
            .tenantId("tenant-" + processDefinitionKey);

    return builderFunction.apply(builder).build();
  }

  public static ProcessDefinitionDbModel createAndSaveRandomProcessDefinition(
      final RdbmsWriters rdbmsWriters,
      final Function<ProcessDefinitionDbModelBuilder, ProcessDefinitionDbModelBuilder>
          builderFunction) {
    final ProcessDefinitionDbModel randomized =
        ProcessDefinitionFixtures.createRandomized(builderFunction);
    rdbmsWriters.getProcessDefinitionWriter().create(randomized);

    rdbmsWriters.flush();

    return randomized;
  }

  public static void createAndSaveRandomProcessDefinitions(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomProcessDefinitions(rdbmsWriters, b -> b.processDefinitionId(nextStringId()));
  }

  public static void createAndSaveRandomProcessDefinitions(
      final RdbmsWriters rdbmsWriters,
      final Function<ProcessDefinitionDbModelBuilder, ProcessDefinitionDbModelBuilder>
          builderFunction) {
    createAndSaveRandomProcessDefinitions(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomProcessDefinitions(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<ProcessDefinitionDbModelBuilder, ProcessDefinitionDbModelBuilder>
          builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters
          .getProcessDefinitionWriter()
          .create(ProcessDefinitionFixtures.createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }

  public static ProcessDefinitionDbModel createAndSaveProcessDefinition(
      final RdbmsWriters rdbmsWriters,
      final Function<ProcessDefinitionDbModelBuilder, ProcessDefinitionDbModelBuilder>
          builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveProcessDefinitions(rdbmsWriters, List.of(definition));
    return definition;
  }

  public static void createAndSaveProcessDefinition(
      final RdbmsWriters rdbmsWriters, final ProcessDefinitionDbModel processDefinition) {
    createAndSaveProcessDefinitions(rdbmsWriters, List.of(processDefinition));
  }

  public static void createAndSaveProcessDefinitions(
      final RdbmsWriters rdbmsWriters, final List<ProcessDefinitionDbModel> processDefinitionList) {
    for (final ProcessDefinitionDbModel processDefinition : processDefinitionList) {
      rdbmsWriters.getProcessDefinitionWriter().create(processDefinition);
    }
    rdbmsWriters.flush();
  }
}
