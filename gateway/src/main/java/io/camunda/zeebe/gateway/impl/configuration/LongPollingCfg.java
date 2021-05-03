/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.configuration;

import java.util.Objects;

public final class LongPollingCfg {

  private boolean enabled = ConfigurationDefaults.DEFAULT_LONG_POLLING_ENABLED;

  public boolean isEnabled() {
    return enabled;
  }

  public LongPollingCfg setEnabled(final boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final LongPollingCfg that = (LongPollingCfg) o;
    return enabled == that.enabled;
  }

  @Override
  public String toString() {
    return "LongPollingCfg{" + "enabled=" + enabled + '}';
  }
}
