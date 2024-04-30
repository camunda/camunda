/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.topology.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
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
  ClusterOperationApplier getApplier(TopologyChangeOperation operation);

  /** An operation applier that can apply and operation and changes the ClusterTopology. */
  interface ClusterOperationApplier {

    /**
     * This method will be called before invoking {@link ClusterOperationApplier#apply()}. This
     * method can be used to validate the operation and to update the ClusterTopology to mark the
     * start of the operation. For example, an operation for joining a partition can mark the state
     * as JOINING.
     *
     * @return an either which contains an exception if the operation is not valid, or a function to
     *     update the cluster topology
     */
    Either<Exception, UnaryOperator<ClusterTopology>> init(
        final ClusterTopology currentClusterTopology);

    /**
     * Applies the operation. This can be run asynchronously and should complete the future when the
     * operation is completed. The future should be completed with a function that can update the
     * ClusterTopology to mark the operation as completed. For example, an operation for joining a
     * partition should mark the state of the partition as ACTIVE.
     *
     * <p>It is expected that no other operation is applied until this operation is completed. It is
     * guaranteed that the ClusterTopology updated by {@link ClusterTopology#init()} remains the
     * same until this operation is completed.
     *
     * @return the future which is completed when the operation is completed successfully or failed.
     */
    ActorFuture<UnaryOperator<ClusterTopology>> apply();
  }

  /**
   * An operation applier that can apply an operation and changes the MemberState of the member that
   * is applying the operation.
   */
  interface MemberOperationApplier extends ClusterOperationApplier {

    @Override
    default Either<Exception, UnaryOperator<ClusterTopology>> init(
        final ClusterTopology currentClusterTopology) {
      return initMemberState(currentClusterTopology)
          .map(transformer -> cluster -> cluster.updateMember(memberId(), transformer));
    }

    @Override
    default ActorFuture<UnaryOperator<ClusterTopology>> apply() {
      final var future = new CompletableActorFuture<UnaryOperator<ClusterTopology>>();
      applyOperation()
          .onComplete(
              (transformer, error) -> {
                if (error == null) {
                  future.complete(cluster -> cluster.updateMember(memberId(), transformer));
                } else {
                  future.completeExceptionally(error);
                }
              });
      return future;
    }

    MemberId memberId();

    /**
     * This method will be called before invoking {@link MemberOperationApplier#applyOperation()}.
     * This method can be used to validate the operation and to update the ClusterTopology to mark
     * the start of the operation. For example, an operation for joining a partition can mark the
     * state as JOINING.
     *
     * @return an either which contains an exception if the operation is not valid, or a function to
     *     update the cluster topology
     */
    Either<Exception, UnaryOperator<MemberState>> initMemberState(
        final ClusterTopology currentClusterTopology);

    /**
     * Applies the operation. This can be run asynchronously and should complete the future when the
     * operation is completed. The future should be completed with a function that can update the
     * ClusterTopology to mark the operation as completed. For example, an operation for joining a
     * partition should mark the state of the partition as ACTIVE.
     *
     * <p>It is expected that no other operation is applied until this operation is completed. It is
     * guaranteed that the MemberState updated by {@link
     * MemberOperationApplier#initMemberState(ClusterTopology)} remains the same until this
     * operation is completed.
     *
     * @return the future which is completed when the operation is completed successfully or failed.
     */
    ActorFuture<UnaryOperator<MemberState>> applyOperation();
  }
}
