/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

public final class ProcessInstanceFixtures extends CommonFixtures {

  private ProcessInstanceFixtures() {}

  public static ProcessInstanceDbModel createRandomized(
      final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder>
          builderFunction) {
    final var builder =
        new ProcessInstanceDbModelBuilder()
            .processInstanceKey(nextKey())
            .processDefinitionKey(nextKey())
            .processDefinitionId("process-" + RANDOM.nextInt(10000))
            .parentProcessInstanceKey(nextKey())
            .parentElementInstanceKey(nextKey())
            .state(randomEnum(ProcessInstanceEntity.ProcessInstanceState.class))
            .startDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .endDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .version(RANDOM.nextInt(10000))
            .tenantId("tenant-" + RANDOM.nextInt(10000));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomProcessInstances(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomProcessInstances(rdbmsWriter, b -> b);
  }

  public static void createAndSaveRandomProcessInstances(
      final RdbmsWriter rdbmsWriter,
      final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder>
          builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getProcessInstanceWriter()
          .create(ProcessInstanceFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static ProcessInstanceDbModel createAndSaveRandomProcessInstance(
      final RdbmsWriter rdbmsWriter,
      final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder>
          builderFunction) {

    final ProcessInstanceDbModel processInstance =
        ProcessInstanceFixtures.createRandomized(builderFunction);
    rdbmsWriter.getProcessInstanceWriter().create(processInstance);

    rdbmsWriter.flush();

    return processInstance;
  }

  public static void createAndSaveProcessInstance(
      final RdbmsWriter rdbmsWriter, final ProcessInstanceDbModel processInstance) {
    createAndSaveProcessInstances(rdbmsWriter, List.of(processInstance));
  }

  public static void createAndSaveProcessInstances(
      final RdbmsWriter rdbmsWriter, final List<ProcessInstanceDbModel> processInstanceList) {
    for (final ProcessInstanceDbModel processInstance : processInstanceList) {
      rdbmsWriter.getProcessInstanceWriter().create(processInstance);
    }
    rdbmsWriter.flush();
  }
}
