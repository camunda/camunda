/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.CorrelatedMessageDbModel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class CorrelatedMessageFixtures extends CommonFixtures {

  private CorrelatedMessageFixtures() {}

  public static CorrelatedMessageDbModel createRandomized(
      final Function<CorrelatedMessageDbModel.Builder, CorrelatedMessageDbModel.Builder>
          builderFunction) {
    final var builder =
        new CorrelatedMessageDbModel.Builder()
            .messageKey(nextKey())
            .subscriptionKey(nextKey())
            .correlationKey("corr-" + generateRandomString(10))
            .correlationTime(OffsetDateTime.now())
            .flowNodeId("flowNode-" + generateRandomString(10))
            .flowNodeInstanceKey(nextKey())
            .messageName("msg-" + generateRandomString(10))
            .partitionId(0)
            .processDefinitionId("procDef-" + generateRandomString(10))
            .processDefinitionKey(nextKey())
            .processInstanceKey(nextKey())
            .tenantId("tenant-" + generateRandomString(10));
    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomCorrelatedMessages(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomCorrelatedMessages(rdbmsWriter, b -> b);
  }

  public static void createAndSaveRandomCorrelatedMessages(
      final RdbmsWriter rdbmsWriter,
      final Function<CorrelatedMessageDbModel.Builder, CorrelatedMessageDbModel.Builder>
          builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter.getCorrelatedMessageWriter().create(createRandomized(builderFunction));
    }
    rdbmsWriter.flush();
  }

  public static CorrelatedMessageDbModel createAndSaveCorrelatedMessage(
      final RdbmsWriter rdbmsWriter,
      final Function<CorrelatedMessageDbModel.Builder, CorrelatedMessageDbModel.Builder>
          builderFunction) {
    final CorrelatedMessageDbModel randomized = createRandomized(builderFunction);
    createAndSaveCorrelatedMessages(rdbmsWriter, List.of(randomized));
    return randomized;
  }

  public static void createAndSaveCorrelatedMessage(
      final RdbmsWriter rdbmsWriter, final CorrelatedMessageDbModel model) {
    createAndSaveCorrelatedMessages(rdbmsWriter, List.of(model));
  }

  public static void createAndSaveCorrelatedMessages(
      final RdbmsWriter rdbmsWriter, final List<CorrelatedMessageDbModel> list) {
    for (final CorrelatedMessageDbModel model : list) {
      rdbmsWriter.getCorrelatedMessageWriter().create(model);
    }
    rdbmsWriter.flush();
  }

  public static String nextStringId() {
    return UUID.randomUUID().toString();
  }
}
