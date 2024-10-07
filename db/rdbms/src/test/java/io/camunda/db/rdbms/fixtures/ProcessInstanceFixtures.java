/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.fixtures;

import io.camunda.db.rdbms.domain.ProcessInstanceDbModel;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.function.Function;

public final class ProcessInstanceFixtures {

  private static final Random RANDOM = new Random(System.nanoTime());
  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private ProcessInstanceFixtures() {
  }

  public static ProcessInstanceDbModel createRandomized(Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder> builderFunction) {
    var builder = new ProcessInstanceDbModelBuilder()
        .processInstanceKey(RANDOM.nextLong())
        .processDefinitionKey(RANDOM.nextLong())
        .bpmnProcessId("process-" + RANDOM.nextInt(1000))
        .parentProcessInstanceKey(RANDOM.nextLong())
        .parentElementInstanceKey(RANDOM.nextLong())
        .startDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
        .endDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
        .version(RANDOM.nextInt(20))
        .tenantId("tenant-" + RANDOM.nextInt(1000));

    return builderFunction.apply(builder).build();
  }

}
