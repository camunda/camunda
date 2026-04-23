/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel.Builder;
import io.camunda.search.entities.MessageSubscriptionEntity.InputSpecItem;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.Map;
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
            .processInstanceKey(nextKey())
            .rootProcessInstanceKey(nextKey())
            .processDefinitionName("processDefinitionName-" + UUID.randomUUID())
            .processDefinitionVersion((int) (Math.random() * 50))
            .messageSubscriptionType(randomEnum(MessageSubscriptionType.class))
            .messageSubscriptionState(randomEnum(MessageSubscriptionState.class))
            .extensionProperties(Map.of("key-" + UUID.randomUUID(), "value-" + UUID.randomUUID()))
            .inputSpecification(
                List.of(
                    new InputSpecItem(
                        "name-" + UUID.randomUUID(), "desc", "type", true, "schema")));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomMessageSubscriptions(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomMessageSubscriptions(rdbmsWriters, b -> b);
  }

  public static MessageSubscriptionDbModel createAndSaveRandomMessageSubscription(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    final var definition = MessageSubscriptionFixtures.createRandomized(builderFunction);
    rdbmsWriters.getMessageSubscriptionWriter().create(definition);
    rdbmsWriters.flush();
    return definition;
  }

  public static void createAndSaveRandomMessageSubscriptions(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    createAndSaveRandomMessageSubscriptions(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomMessageSubscriptions(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters
          .getMessageSubscriptionWriter()
          .create(MessageSubscriptionFixtures.createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }

  public static MessageSubscriptionDbModel createAndSaveMessageSubscription(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveMessageSubscriptions(rdbmsWriters, List.of(definition));
    return definition;
  }

  public static void createAndSaveMessageSubscription(
      final RdbmsWriters rdbmsWriters, final MessageSubscriptionDbModel group) {
    createAndSaveMessageSubscriptions(rdbmsWriters, List.of(group));
  }

  public static void createAndSaveMessageSubscriptions(
      final RdbmsWriters rdbmsWriters, final List<MessageSubscriptionDbModel> groupList) {
    for (final MessageSubscriptionDbModel group : groupList) {
      rdbmsWriters.getMessageSubscriptionWriter().create(group);
    }
    rdbmsWriters.flush();
  }
}
