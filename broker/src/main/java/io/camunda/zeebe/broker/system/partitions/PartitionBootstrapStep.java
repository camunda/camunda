/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.util.sched.future.ActorFuture;

/**
 * A PartitionBoostrapStep is an action to be taken while opening or closing a partition (e.g.,
 * opening/closing a component of the partition). The steps are opened in a pre-defined order and
 * will be closed in the reverse order.
 */
public interface PartitionBootstrapStep {
  /**
   * Performs some action required for the partition to function. This may include opening
   * components (e.g., logstream), setting their values in {@link PartitionBootstrapContext}, etc.
   * The subsequent partition steps will only be opened after the returned future is completed.
   *
   * @param context the partition boostrap context
   * @return future
   */
  ActorFuture<PartitionBootstrapContext> open(final PartitionBootstrapContext context);

  /**
   * Perform tear-down actions to clear the partition and prepare for another one to be installed.
   * This includes closing components, clearing their values from {@link PartitionBootstrapContext}
   * so they may be garbage-collected, etc. The subsequent partition steps will only be closed after
   * the returned future is completed.
   *
   * @param context the partition bootstrap context
   * @return future
   */
  ActorFuture<PartitionBootstrapContext> close(final PartitionBootstrapContext context);

  /** @return A log-friendly identification of the PartitionStep. */
  String getName();
}
