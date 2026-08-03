/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import io.camunda.zeebe.dynamic.config.state.ExportingState;

/**
 * Applies an exporting state change through the dynamic cluster configuration, so the change is
 * durable across restarts. Implementations block until the change has been fully applied.
 */
@FunctionalInterface
public interface ExportingStateChanger {

  /**
   * Requests the cluster to move to the given exporting state and blocks until the change has been
   * applied on this node.
   */
  void changeExportingState(ExportingState state);
}
