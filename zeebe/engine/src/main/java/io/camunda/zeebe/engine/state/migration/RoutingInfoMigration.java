/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

public class RoutingInfoMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return "RoutingInfoMigration";
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    return !context.processingState().getRoutingState().isInitialized();
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    final var routingState = context.processingState().getRoutingState();
    final var partitionCount = context.clusterContext().partitionCount();
    routingState.initializeRoutingInfo(partitionCount);
  }
}
