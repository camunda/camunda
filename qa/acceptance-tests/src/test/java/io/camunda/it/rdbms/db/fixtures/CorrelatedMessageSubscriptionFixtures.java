/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.CorrelatedMessageSubscriptionDbModel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class CorrelatedMessageSubscriptionFixtures extends CommonFixtures {

  private CorrelatedMessageSubscriptionFixtures() {}

  public static CorrelatedMessageSubscriptionDbModel createRandomized(
      final Function<
              CorrelatedMessageSubscriptionDbModel.Builder,
              CorrelatedMessageSubscriptionDbModel.Builder>
          builderFunction) {
    final var builder =
        new CorrelatedMessageSubscriptionDbModel.Builder()
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

  public static void createAndSaveRandomCorrelatedMessageSubscriptions(
      final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomCorrelatedMessageSubscriptions(rdbmsWriter, b -> b);
  }

  public static void createAndSaveRandomCorrelatedMessageSubscriptions(
      final RdbmsWriter rdbmsWriter,
      final Function<
              CorrelatedMessageSubscriptionDbModel.Builder,
              CorrelatedMessageSubscriptionDbModel.Builder>
          builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getCorrelatedMessageSubscriptionWriter()
          .create(createRandomized(builderFunction));
    }
    rdbmsWriter.flush();
  }

  public static CorrelatedMessageSubscriptionDbModel createAndSaveCorrelatedMessageSubscription(
      final RdbmsWriter rdbmsWriter,
      final Function<
              CorrelatedMessageSubscriptionDbModel.Builder,
              CorrelatedMessageSubscriptionDbModel.Builder>
          builderFunction) {
    final CorrelatedMessageSubscriptionDbModel randomized = createRandomized(builderFunction);
    createAndSaveCorrelatedMessageSubscriptions(rdbmsWriter, List.of(randomized));
    return randomized;
  }

  public static void createAndSaveCorrelatedMessageSubscription(
      final RdbmsWriter rdbmsWriter, final CorrelatedMessageSubscriptionDbModel model) {
    createAndSaveCorrelatedMessageSubscriptions(rdbmsWriter, List.of(model));
  }

  public static void createAndSaveCorrelatedMessageSubscriptions(
      final RdbmsWriter rdbmsWriter, final List<CorrelatedMessageSubscriptionDbModel> list) {
    for (final CorrelatedMessageSubscriptionDbModel model : list) {
      rdbmsWriter.getCorrelatedMessageSubscriptionWriter().create(model);
    }
    rdbmsWriter.flush();
  }

  public static String nextStringId() {
    return UUID.randomUUID().toString();
  }
}
