/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.util.ecv.CapabilitySet;

/** The enabled capabilities of this partition, as committed through the log. */
public interface ClusterCapabilitiesState {

  /** Returns the enabled capabilities of this partition; empty before the first commit. */
  CapabilitySet getEnabledCapabilities();

  /**
   * Returns the capabilities that this broker supports. The engine refuses to apply any other
   * capability (the engine guard). Not persisted.
   */
  CapabilitySet getSupportedCapabilities();
}
