/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions;

import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

/**
 * A PartitionStep is an action to be taken while opening or closing a partition (e.g.,
 * opening/closing a component of the partition). The steps are opened in a pre-defined order and
 * will be closed in the reverse order.
 */
public interface PartitionStep {

  /**
   * Performs some action required for the partition to function. This may include opening
   * components (e.g., logstream), setting their values in {@link PartitionContext}, etc. The
   * subsequent partition steps will only be opened after the returned future is completed.
   *
   * @param currentTerm the current term of the transition
   * @param context the partition context
   * @return future
   */
  default ActorFuture<Void> open(final long currentTerm, final PartitionContext context) {
    return open(context);
  }

  /**
   * Performs some action required for the partition to function. This may include opening
   * components (e.g., logstream), setting their values in {@link PartitionContext}, etc. The
   * subsequent partition steps will only be opened after the returned future is completed.
   *
   * @param context the partition context
   * @return future
   */
  default ActorFuture<Void> open(final PartitionContext context) {
    return CompletableActorFuture.completed(null);
  }

  /**
   * Perform tear-down actions to clear the partition and prepare for another one to be installed.
   * This includes closing components, clearing their values from {@link PartitionContext} so they
   * may be garbage-collected, etc. The subsequent partition steps will only be closed after the
   * returned future is completed.
   *
   * @param context the partition context
   * @return future
   */
  ActorFuture<Void> close(final PartitionContext context);

  /** @return A log-friendly identification of the PartitionStep. */
  String getName();
}
