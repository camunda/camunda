/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ClusterCapabilitiesState;
import io.camunda.zeebe.util.ecv.CapabilitySet;

public interface MutableClusterCapabilitiesState extends ClusterCapabilitiesState {

  /** Adds the capabilities to the enabled capabilities; the result is never smaller. */
  void addEnabledCapabilities(CapabilitySet capabilities);

  /** Sets what this broker supports; called once when the engine is created. */
  void setSupportedCapabilities(CapabilitySet supported);
}
