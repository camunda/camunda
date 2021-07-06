/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.util.sched.future.ActorFuture;

/**
 * A PartitionTransitionStep is an action to be taken while transitioning the partition to a new
 * role
 */
public interface PartitionTransitionStep {

  ActorFuture<PartitionTransitionContextImpl> transitionTo(
      final PartitionTransitionContextImpl context, final long term, final Role role);

  /** @return A log-friendly identification of the PartitionTransitionStep. */
  String getName();
}
