/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class ProcessInstanceFixtures {

  private static final AtomicLong ID_COUNTER = new AtomicLong();
  private static final Random RANDOM = new Random(System.nanoTime());
  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private ProcessInstanceFixtures() {}

  public static Long nextId() {
    return ID_COUNTER.incrementAndGet();
  }

  public static ProcessInstanceDbModel createRandomized(
      final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder>
          builderFunction) {
    final var builder =
        new ProcessInstanceDbModelBuilder()
            .processInstanceKey(nextId())
            .processDefinitionKey(nextId())
            .bpmnProcessId("process-" + RANDOM.nextInt(1000))
            .parentProcessInstanceKey(nextId())
            .parentElementInstanceKey(nextId())
            .startDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .endDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .version(RANDOM.nextInt(20))
            .tenantId("tenant-" + RANDOM.nextInt(1000));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomProcessInstances(final RdbmsWriter rdbmsWriter) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getProcessInstanceWriter()
          .create(ProcessInstanceFixtures.createRandomized(b -> b));
    }

    rdbmsWriter.flush();
  }

  public static void createAndSaveProcessInstance(final RdbmsWriter rdbmsWriter, final ProcessInstanceDbModel processInstance) {
    createAndSaveProcessInstances(rdbmsWriter, List.of(processInstance));
  }

  public static void createAndSaveProcessInstances(
      final RdbmsWriter rdbmsWriter,
      final List<ProcessInstanceDbModel> processInstanceList) {
    for (final ProcessInstanceDbModel processInstance : processInstanceList) {
      rdbmsWriter.getProcessInstanceWriter().create(processInstance);
    }
    rdbmsWriter.flush();
  }


  public static void createAndSaveProcessDefinition(final RdbmsWriter rdbmsWriter, final ProcessDefinitionDbModel processDefinition) {
    rdbmsWriter.getProcessDefinitionWriter().save(processDefinition);
    rdbmsWriter.flush();
  }

}
