/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning;

import io.camunda.zeebe.scheduler.future.ActorFuture;

/** A single step in the partition startup/shutdown sequence. */
public interface PartitionStartupStep {

  String getName();

  default void prepare(final PartitionContext context) {}

  ActorFuture<Void> activate(PartitionContext context);

  ActorFuture<Void> deactivate(PartitionContext context);
}
