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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class ProcessDefinitionFixtures {

  private static final AtomicLong ID_COUNTER = new AtomicLong();
  private static final Random RANDOM = new Random(System.nanoTime());
  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private ProcessDefinitionFixtures() {
  }

  public static Long nextKey() {
    return ID_COUNTER.incrementAndGet();
  }

  public static String nextStringId() {
    return UUID.randomUUID().toString();
  }

  public static ProcessDefinitionDbModel createRandomized(
      final Function<ProcessDefinitionDbModelBuilder, ProcessDefinitionDbModelBuilder>
          builderFunction) {
    final var processDefinitionKey = ID_COUNTER.incrementAndGet();
    final var version = RANDOM.nextInt(100);
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
    createAndSaveRandomProcessDefinitions(rdbmsWriter, nextStringId());
  }

  public static void createAndSaveRandomProcessDefinitions(
      final RdbmsWriter rdbmsWriter, final String processDefinitionId) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getProcessDefinitionWriter()
          .create(
              ProcessDefinitionFixtures.createRandomized(
                  b -> b.processDefinitionId(processDefinitionId)));
    }

    rdbmsWriter.flush();
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
