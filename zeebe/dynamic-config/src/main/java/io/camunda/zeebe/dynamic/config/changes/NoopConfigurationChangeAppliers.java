/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * This is temporary implementation for TopologyChangeAppliers. This will be eventually removed or
 * moved to tests, once concrete implementation for each TopologyChangeOperation is available.
 */
public class NoopConfigurationChangeAppliers implements ConfigurationChangeAppliers {

  @Override
  public MemberOperationApplier getApplier(final ClusterConfigurationChangeOperation operation) {
    return new NoopApplier(operation.memberId());
  }

  public static class NoopApplier implements MemberOperationApplier {

    private final MemberId memberId;

    public NoopApplier(final MemberId memberId) {
      this.memberId = memberId;
    }

    @Override
    public MemberId memberId() {
      return memberId;
    }

    @Override
    public Either<Exception, UnaryOperator<MemberState>> initMemberState(
        final ClusterConfiguration currentClusterConfiguration) {
      return Either.right(memberState -> memberState);
    }

    @Override
    public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
      return CompletableActorFuture.completed(memberState -> memberState);
    }
  }
}
