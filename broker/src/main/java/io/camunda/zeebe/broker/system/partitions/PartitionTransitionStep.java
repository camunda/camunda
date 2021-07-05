/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.protocol.record.PartitionRole;
import io.camunda.zeebe.util.sched.future.ActorFuture;

/**
 * A PartitionStep is an action to be taken while opening or closing a partition (e.g.,
 * opening/closing a component of the partition). The steps are opened in a pre-defined order and
 * will be closed in the reverse order.
 */
public interface PartitionTransitionStep {

  ActorFuture<PartitionTransitionContext> transitionTo(
      final PartitionTransitionContext context,
      final long currentTerm,
      final PartitionRole currentRole,
      final long nextTerm,
      final PartitionRole targetRole);

  /** @return A log-friendly identification of the PartitionStep. */
  String getName();
}
