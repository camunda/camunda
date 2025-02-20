/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
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
            .tenantId("tenant-" + RANDOM.nextInt(1000));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomProcessDefinitions(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomProcessDefinitions(rdbmsWriter, b -> b.processDefinitionId(nextStringId()));
  }

  public static void createAndSaveRandomProcessDefinitions(
      final RdbmsWriter rdbmsWriter,
      final Function<ProcessDefinitionDbModelBuilder, ProcessDefinitionDbModelBuilder>
          builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getProcessDefinitionWriter()
          .create(ProcessDefinitionFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static ProcessDefinitionDbModel createAndSaveProcessDefinition(
      final RdbmsWriter rdbmsWriter,
      final Function<ProcessDefinitionDbModelBuilder, ProcessDefinitionDbModelBuilder>
          builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveProcessDefinitions(rdbmsWriter, List.of(definition));
    return definition;
  }

  public static void createAndSaveProcessDefinition(
      final RdbmsWriter rdbmsWriter, final ProcessDefinitionDbModel processDefinition) {
    createAndSaveProcessDefinitions(rdbmsWriter, List.of(processDefinition));
  }

  public static void createAndSaveProcessDefinitions(
      final RdbmsWriter rdbmsWriter, final List<ProcessDefinitionDbModel> processDefinitionList) {
    for (final ProcessDefinitionDbModel processDefinition : processDefinitionList) {
      rdbmsWriter.getProcessDefinitionWriter().create(processDefinition);
    }
    rdbmsWriter.flush();
  }
}
