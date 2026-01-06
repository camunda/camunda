/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel.Builder;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class MessageSubscriptionFixtures extends CommonFixtures {

  private MessageSubscriptionFixtures() {}

  public static MessageSubscriptionDbModel createRandomized(
      final Function<Builder, Builder> builderFunction) {
    final var groupKey = nextKey();
    final var groupId = Strings.newRandomValidIdentityId();
    final var builder =
        new Builder()
            .messageName("messageName-" + UUID.randomUUID())
            .correlationKey("correlationKey-" + UUID.randomUUID())
            .tenantId("tenant-" + UUID.randomUUID())
            .partitionId((int) (Math.random() * 10))
            .flowNodeId("flowNode-" + UUID.randomUUID())
            .messageSubscriptionKey(nextKey())
            .processDefinitionId("processDefinitionId-" + UUID.randomUUID())
            .processInstanceKey(nextKey());

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomMessageSubscriptions(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomMessageSubscriptions(rdbmsWriter, b -> b);
  }

  public static MessageSubscriptionDbModel createAndSaveRandomMessageSubscription(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var definition = MessageSubscriptionFixtures.createRandomized(builderFunction);
    rdbmsWriter.getMessageSubscriptionWriter().create(definition);
    rdbmsWriter.flush();
    return definition;
  }

  public static void createAndSaveRandomMessageSubscriptions(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getMessageSubscriptionWriter()
          .create(MessageSubscriptionFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static MessageSubscriptionDbModel createAndSaveMessageSubscription(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveMessageSubscriptions(rdbmsWriter, List.of(definition));
    return definition;
  }

  public static void createAndSaveMessageSubscription(
      final RdbmsWriter rdbmsWriter, final MessageSubscriptionDbModel group) {
    createAndSaveMessageSubscriptions(rdbmsWriter, List.of(group));
  }

  public static void createAndSaveMessageSubscriptions(
      final RdbmsWriter rdbmsWriter, final List<MessageSubscriptionDbModel> groupList) {
    for (final MessageSubscriptionDbModel group : groupList) {
      rdbmsWriter.getMessageSubscriptionWriter().create(group);
    }
    rdbmsWriter.flush();
  }
}
