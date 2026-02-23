/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
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
            .rootProcessInstanceKey(nextKey())
            .tenantId("tenant-" + generateRandomString(10));
    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomCorrelatedMessageSubscriptions(
      final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomCorrelatedMessageSubscriptions(rdbmsWriters, b -> b);
  }

  public static void createAndSaveRandomCorrelatedMessageSubscriptions(
      final RdbmsWriters rdbmsWriters,
      final Function<
              CorrelatedMessageSubscriptionDbModel.Builder,
              CorrelatedMessageSubscriptionDbModel.Builder>
          builderFunction) {
    createAndSaveRandomCorrelatedMessageSubscriptions(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomCorrelatedMessageSubscriptions(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<
              CorrelatedMessageSubscriptionDbModel.Builder,
              CorrelatedMessageSubscriptionDbModel.Builder>
          builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters
          .getCorrelatedMessageSubscriptionWriter()
          .create(createRandomized(builderFunction));
    }
    rdbmsWriters.flush();
  }

  public static CorrelatedMessageSubscriptionDbModel createAndSaveCorrelatedMessageSubscription(
      final RdbmsWriters rdbmsWriters,
      final Function<
              CorrelatedMessageSubscriptionDbModel.Builder,
              CorrelatedMessageSubscriptionDbModel.Builder>
          builderFunction) {
    final CorrelatedMessageSubscriptionDbModel randomized = createRandomized(builderFunction);
    createAndSaveCorrelatedMessageSubscriptions(rdbmsWriters, List.of(randomized));
    return randomized;
  }

  public static void createAndSaveCorrelatedMessageSubscription(
      final RdbmsWriters rdbmsWriters, final CorrelatedMessageSubscriptionDbModel model) {
    createAndSaveCorrelatedMessageSubscriptions(rdbmsWriters, List.of(model));
  }

  public static void createAndSaveCorrelatedMessageSubscriptions(
      final RdbmsWriters rdbmsWriters, final List<CorrelatedMessageSubscriptionDbModel> list) {
    for (final CorrelatedMessageSubscriptionDbModel model : list) {
      rdbmsWriters.getCorrelatedMessageSubscriptionWriter().create(model);
    }
    rdbmsWriters.flush();
  }

  public static String nextStringId() {
    return UUID.randomUUID().toString();
  }
}
