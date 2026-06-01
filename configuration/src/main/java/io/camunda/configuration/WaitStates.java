/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateConfiguration;

/**
 * Configuration for wait-state tracking.
 *
 * <p>Maps to the {@code camunda.data.wait-states} property namespace. When disabled, no wait-state
 * rows are written to or deleted from secondary storage.
 */
public class WaitStates {

  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  /** Converts this configuration to a {@link WaitStateConfiguration} for the exporters. */
  public WaitStateConfiguration toConfiguration() {
    return new WaitStateConfiguration().setEnabled(enabled);
  }
}
