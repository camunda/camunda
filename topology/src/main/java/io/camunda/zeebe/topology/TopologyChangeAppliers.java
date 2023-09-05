/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/** */
public interface TopologyChangeAppliers {

  /**
   * @return the operation applier for the given operation
   */
  OperationApplier getApplier(TopologyChangeOperation operation);

  interface OperationApplier {

    /**
     * Validate and initialize the operation. Returns an Either with an exception if the operation
     * is not valid, or a function that updates the state of the member in the cluster topology
     *
     * @return an either
     */
    Either<Exception, UnaryOperator<MemberState>> init();

    /**
     * Applies the operation. This can be run asynchronously and should complete the future when the
     * operation is completed.
     *
     * @return the future when the operation is completed.
     */
    ActorFuture<UnaryOperator<MemberState>> apply();
  }
}
