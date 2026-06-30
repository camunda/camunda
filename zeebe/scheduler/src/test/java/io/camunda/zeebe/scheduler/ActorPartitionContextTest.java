/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static io.camunda.zeebe.scheduler.Actor.ACTOR_PROP_PARTITION_ID;
import static io.camunda.zeebe.scheduler.Actor.ACTOR_PROP_PHYSICAL_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PartitionId;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ActorPartitionContextTest {

  @Test
  void shouldExposePartitionIdAndGroupInActorContext() {
    // given
    final var actor = new PartitionScopedActor(new PartitionId("my-group", 3));

    // when
    final Map<String, String> context = actor.getContext();

    // then
    assertThat(context)
        .containsEntry(ACTOR_PROP_PARTITION_ID, "3")
        .containsEntry(ACTOR_PROP_PHYSICAL_TENANT, "my-group");
  }

  private static final class PartitionScopedActor extends Actor {
    private final PartitionId partitionId;

    private PartitionScopedActor(final PartitionId partitionId) {
      this.partitionId = partitionId;
    }

    @Override
    protected Map<String, String> createContext() {
      final var context = super.createContext();
      putPartitionContext(context, partitionId);
      return context;
    }
  }
}
