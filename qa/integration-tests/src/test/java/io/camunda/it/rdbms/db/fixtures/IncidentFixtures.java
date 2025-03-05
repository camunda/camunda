/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

public final class IncidentFixtures extends CommonFixtures {

  private IncidentFixtures() {
  }

  public static IncidentDbModel createRandomized(
      final Function<IncidentDbModel.Builder, IncidentDbModel.Builder> builderFunction) {
    final var builder =
        new IncidentDbModel.Builder()
            .incidentKey(nextKey())
            .processDefinitionKey(nextKey())
            .processDefinitionId("process-" + generateRandomString(20))
            .flowNodeInstanceKey(nextKey())
            .processInstanceKey(nextKey())
            .jobKey(nextKey())
            .flowNodeId("flowNode-" + nextKey())
            .state(randomEnum(IncidentState.class))
            .errorType(randomEnum(ErrorType.class))
            .errorMessage("error-" + generateRandomString(20))
            .creationDate(NOW.plus(RANDOM.nextInt(), ChronoUnit.MILLIS))
            .tenantId("tenant-" + generateRandomString(20))
            .treePath("tree-" + generateRandomString(20))
            .partitionId(RANDOM.nextInt(100));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomIncidents(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomIncidents(rdbmsWriter, b -> b);
  }

  public static void createAndSaveRandomIncidents(
      final RdbmsWriter rdbmsWriter,
      final Function<IncidentDbModel.Builder, IncidentDbModel.Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter.getIncidentWriter().create(IncidentFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static IncidentDbModel createAndSaveIncident(
      final RdbmsWriter rdbmsWriter,
      final Function<IncidentDbModel.Builder, IncidentDbModel.Builder> builderFunction) {
    final IncidentDbModel randomized = createRandomized(builderFunction);
    createAndSaveIncidents(rdbmsWriter, List.of(randomized));
    return randomized;
  }

  public static void createAndSaveIncident(
      final RdbmsWriter rdbmsWriter, final IncidentDbModel incident) {
    createAndSaveIncidents(rdbmsWriter, List.of(incident));
  }

  public static void createAndSaveIncidents(
      final RdbmsWriter rdbmsWriter, final List<IncidentDbModel> incidentList) {
    for (final IncidentDbModel incident : incidentList) {
      rdbmsWriter.getIncidentWriter().create(incident);
    }
    rdbmsWriter.flush();
  }
}
