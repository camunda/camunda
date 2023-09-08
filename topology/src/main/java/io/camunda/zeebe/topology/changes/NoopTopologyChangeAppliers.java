/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * This is temporary implementation for TopologyChangeAppliers. This will be eventually removed or
 * moved to tests, once concrete implementation for each TopologyChangeOperation is available.
 */
public class NoopTopologyChangeAppliers implements TopologyChangeAppliers {

  @Override
  public OperationApplier getApplier(final TopologyChangeOperation operation) {
    return new NoopApplier();
  }

  public static class NoopApplier implements OperationApplier {

    @Override
    public Either<Exception, UnaryOperator<MemberState>> init(
        final ClusterTopology currentClusterTopology) {
      return Either.right(memberState -> memberState);
    }

    @Override
    public ActorFuture<UnaryOperator<MemberState>> apply() {
      return CompletableActorFuture.completed(memberState -> memberState);
    }
  }
}
