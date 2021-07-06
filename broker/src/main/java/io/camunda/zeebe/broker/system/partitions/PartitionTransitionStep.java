package io.camunda.zeebe.broker.system.partitions;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.util.sched.future.ActorFuture;

/**
 * A PartitionTransitionStep is an action to be taken while transitioning the partition to a new
 * role
 */
public interface PartitionTransitionStep {

  ActorFuture<PartitionTransitionContext> transitionTo(
      final PartitionTransitionContext context, final long term, final Role role);

  /** @return A log-friendly identification of the PartitionTransitionStep. */
  String getName();
}
