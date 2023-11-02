/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

@FunctionalInterface
public interface TopologyChangeAppliers {

  /**
   * @return the operation applier for the given operation
   */
  OperationApplier getApplier(TopologyChangeOperation operation);

  interface OperationApplier {

    /**
     * This method will be called before invoking {@link OperationApplier#apply()}. This method can
     * be used to validate the operation and to update the ClusterTopology to mark the start of the
     * operation. For example, an operation for joining a partition can mark the state as JOINING.
     *
     * @return an either which contains an exception if the operation is not valid, or a function to
     *     update the cluster topology
     */
    Either<Exception, UnaryOperator<MemberState>> init(
        final ClusterTopology currentClusterTopology);

    /**
     * Applies the operation. This can be run asynchronously and should complete the future when the
     * operation is completed. The future should be completed with a function that can update the
     * ClusterTopology to mark the operation as completed. For example, an operation for joining a
     * partition should mark the state of the partition as ACTIVE.
     *
     * <p>It is expected that no other operation is applied until this operation is completed. It is
     * guaranteed that the MemberState updated by {@link OperationApplier#init()} remains the same
     * until this operation is completed.
     *
     * @return the future which is completed when the operation is completed successfully or failed.
     */
    ActorFuture<UnaryOperator<MemberState>> apply();
  }
}
