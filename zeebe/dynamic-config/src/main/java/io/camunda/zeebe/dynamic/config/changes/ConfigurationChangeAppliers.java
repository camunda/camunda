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

@FunctionalInterface
public interface ConfigurationChangeAppliers {

  /**
   * @return the operation applier for the given operation
   */
  ClusterOperationApplier getApplier(ClusterConfigurationChangeOperation operation);

  /** An operation applier that can apply and operation and changes the ClusterConfiguration. */
  interface ClusterOperationApplier {

    /**
     * This method will be called before invoking {@link ClusterOperationApplier#apply()}. This
     * method can be used to validate the operation and to update the ClusterConfiguration to mark
     * the start of the operation. For example, an operation for joining a partition can mark the
     * state as JOINING.
     *
     * @return an either which contains an exception if the operation is not valid, or a function to
     *     update the cluster configuration
     */
    Either<Exception, UnaryOperator<ClusterConfiguration>> init(
        final ClusterConfiguration currentClusterConfiguration);

    /**
     * Applies the operation. This can be run asynchronously and should complete the future when the
     * operation is completed. The future should be completed with a function that can update the
     * ClusterConfiguration to mark the operation as completed. For example, an operation for
     * joining a partition should mark the state of the partition as ACTIVE.
     *
     * <p>It is expected that no other operation is applied until this operation is completed. It is
     * guaranteed that the ClusterConfiguration updated by {@link ClusterConfiguration#init()}
     * remains the same until this operation is completed.
     *
     * @return the future which is completed when the operation is completed successfully or failed.
     */
    ActorFuture<UnaryOperator<ClusterConfiguration>> apply();
  }

  /**
   * An operation applier that can apply an operation and changes the MemberState of the member that
   * is applying the operation.
   */
  interface MemberOperationApplier extends ClusterOperationApplier {

    @Override
    default Either<Exception, UnaryOperator<ClusterConfiguration>> init(
        final ClusterConfiguration currentClusterConfiguration) {
      return initMemberState(currentClusterConfiguration)
          .map(transformer -> cluster -> cluster.updateMember(memberId(), transformer));
    }

    @Override
    default ActorFuture<UnaryOperator<ClusterConfiguration>> apply() {
      final var future = new CompletableActorFuture<UnaryOperator<ClusterConfiguration>>();
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
     * This method can be used to validate the operation and to update the ClusterConfiguration to
     * mark the start of the operation. For example, an operation for joining a partition can mark
     * the state as JOINING.
     *
     * @return an either which contains an exception if the operation is not valid, or a function to
     *     update the cluster configuration
     */
    Either<Exception, UnaryOperator<MemberState>> initMemberState(
        final ClusterConfiguration currentClusterConfiguration);

    /**
     * Applies the operation. This can be run asynchronously and should complete the future when the
     * operation is completed. The future should be completed with a function that can update the
     * ClusterTopology to mark the operation as completed. For example, an operation for joining a
     * partition should mark the state of the partition as ACTIVE.
     *
     * <p>It is expected that no other operation is applied until this operation is completed. It is
     * guaranteed that the MemberState updated by {@link
     * MemberOperationApplier#initMemberState(ClusterConfiguration)} remains the same until this
     * operation is completed.
     *
     * @return the future which is completed when the operation is completed successfully or failed.
     */
    ActorFuture<UnaryOperator<MemberState>> applyOperation();
  }
}
