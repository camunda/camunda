/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Duration;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class SchemaInitializationApplierTest {

  private static final MemberId MEMBER_ID = MemberId.from("1");

  @Test
  void shouldLeaveGroupConfigurationUnchangedOnInit() {
    // given
    final var applier =
        new SchemaInitializationApplier(MEMBER_ID, new SucceedingRestoreChangeExecutor());
    final var groupConfiguration = PartitionGroupConfiguration.empty(1);

    // when
    final var result = applier.init(GlobalConfiguration.init(), groupConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get().apply(groupConfiguration)).isSameAs(groupConfiguration);
  }

  @Test
  void shouldDelegateToExecutorAndLeaveGroupConfigurationUnchanged() {
    // given
    final var executor = new SucceedingRestoreChangeExecutor();
    final var applier = new SchemaInitializationApplier(MEMBER_ID, executor);

    // when
    final var result = applier.apply();

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
    assertThat(executor.schemaInitializationCount).isOne();

    final var groupConfiguration = PartitionGroupConfiguration.empty(1);
    assertThat(result.join().apply(groupConfiguration)).isSameAs(groupConfiguration);
  }

  @Test
  void shouldFailApplyWhenExecutorFails() {
    // given
    final var applier =
        new SchemaInitializationApplier(
            MEMBER_ID, new RestoreChangeExecutor.DeniedRestoreChangeExecutor());

    // when
    final var result = applier.apply();

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("only supported while the broker is in recovery mode");
  }

  private static final class SucceedingRestoreChangeExecutor implements RestoreChangeExecutor {
    private int schemaInitializationCount;

    @Override
    public ActorFuture<Void> preRestore(final int partitionId) {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> initializeSchema() {
      schemaInitializationCount++;
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> restore(final int partitionId, final SortedSet<Long> backupIds) {
      return CompletableActorFuture.completed(null);
    }
  }
}
