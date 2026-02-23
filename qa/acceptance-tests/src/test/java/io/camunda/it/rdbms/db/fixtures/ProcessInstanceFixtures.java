/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
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
            .rootProcessInstanceKey(nextKey())
            .processDefinitionKey(nextKey())
            .processDefinitionId("process-" + RANDOM.nextInt(10000))
            .parentProcessInstanceKey(nextKey())
            .parentElementInstanceKey(nextKey())
            .state(randomEnum(ProcessInstanceEntity.ProcessInstanceState.class))
            .startDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .endDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .version(RANDOM.nextInt(10000))
            .tenantId("tenant-" + RANDOM.nextInt(10000))
            .partitionId(RANDOM.nextInt(10000));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomProcessInstances(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomProcessInstances(rdbmsWriters, b -> b);
  }

  public static void createAndSaveRandomProcessInstances(
      final RdbmsWriters rdbmsWriters,
      final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder>
          builderFunction) {
    createAndSaveRandomProcessInstances(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomProcessInstances(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder>
          builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters
          .getProcessInstanceWriter()
          .create(ProcessInstanceFixtures.createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }

  public static ProcessInstanceDbModel createAndSaveRandomRootProcessInstance(
      final RdbmsWriters rdbmsWriters,
      final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder>
          builderFunction) {
    final long processInstanceKey = nextKey();
    return createAndSaveRandomProcessInstance(
        rdbmsWriters,
        b ->
            builderFunction
                .apply(b)
                .processInstanceKey(processInstanceKey)
                .rootProcessInstanceKey(processInstanceKey));
  }

  public static ProcessInstanceDbModel createAndSaveRandomProcessInstance(
      final RdbmsWriters rdbmsWriters,
      final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder>
          builderFunction) {

    final ProcessInstanceDbModel processInstance =
        ProcessInstanceFixtures.createRandomized(builderFunction);
    rdbmsWriters.getProcessInstanceWriter().create(processInstance);

    rdbmsWriters.flush();

    return processInstance;
  }

  public static void createAndSaveProcessInstance(
      final RdbmsWriters rdbmsWriters, final ProcessInstanceDbModel processInstance) {
    createAndSaveProcessInstances(rdbmsWriters, List.of(processInstance));
  }

  public static void createAndSaveProcessInstances(
      final RdbmsWriters rdbmsWriters, final List<ProcessInstanceDbModel> processInstanceList) {
    for (final ProcessInstanceDbModel processInstance : processInstanceList) {
      rdbmsWriters.getProcessInstanceWriter().create(processInstance);
    }
    rdbmsWriters.flush();
  }
}
