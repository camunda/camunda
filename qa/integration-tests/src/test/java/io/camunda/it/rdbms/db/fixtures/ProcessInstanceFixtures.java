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
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
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
            .processDefinitionId("process-" + RANDOM.nextInt(1000))
            .parentProcessInstanceKey(nextKey())
            .parentElementInstanceKey(nextKey())
            .startDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .endDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .version(RANDOM.nextInt(20))
            .tenantId("tenant-" + RANDOM.nextInt(1000));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomProcessInstances(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomProcessInstances(rdbmsWriter, nextStringId());
  }

  public static void createAndSaveRandomProcessInstances(
      final RdbmsWriter rdbmsWriter, final String processDefinitionId) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getProcessInstanceWriter()
          .create(
              ProcessInstanceFixtures.createRandomized(
                  b -> b.processDefinitionId(processDefinitionId)));
    }

    rdbmsWriter.flush();
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

  public static void createAndSaveProcessDefinition(
      final RdbmsWriter rdbmsWriter, final ProcessDefinitionDbModel processDefinition) {
    rdbmsWriter.getProcessDefinitionWriter().save(processDefinition);
    rdbmsWriter.flush();
  }
}
