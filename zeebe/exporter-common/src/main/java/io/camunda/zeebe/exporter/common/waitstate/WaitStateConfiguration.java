/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

/**
 * Runtime configuration for wait-state tracking.
 *
 * <p>When disabled, no wait-state handlers are registered in either the Camunda or RDBMS exporter,
 * and no rows are written to or deleted from the wait-state index/table.
 *
 * <p>Corresponds to the {@code camunda.data.wait-states.enabled} property (default {@code true}).
 */
public final class WaitStateConfiguration {

  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public WaitStateConfiguration setEnabled(final boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  @Override
  public String toString() {
    return "WaitStateConfiguration{enabled=" + enabled + '}';
  }
}
