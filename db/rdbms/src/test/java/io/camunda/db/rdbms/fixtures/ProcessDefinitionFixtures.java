/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.fixtures;

import io.camunda.db.rdbms.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.domain.ProcessInstanceDbModel;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.function.Function;

public final class ProcessDefinitionFixtures {

  private static final Random RANDOM = new Random(System.nanoTime());
  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private ProcessDefinitionFixtures() {
  }

  public static ProcessDefinitionDbModel createRandomized(Function<ProcessDefinitionDbModelBuilder, ProcessDefinitionDbModelBuilder> builderFunction) {
    var processDefinitionKey = RANDOM.nextLong(1000);
    var version = RANDOM.nextInt(100);
    var builder = new ProcessDefinitionDbModelBuilder()
        .processDefinitionKey(processDefinitionKey)
        .bpmnProcessId("process-" + processDefinitionKey)
        .name("Process " + processDefinitionKey)
        .version(version)
        .versionTag("Version " + version)
        .tenantId("tenant-" + RANDOM.nextInt(1000));

    return builderFunction.apply(builder).build();
  }

}
