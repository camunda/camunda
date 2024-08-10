/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import java.util.Objects;

public class MultiTenancyCfg {

  private boolean enabled = false;

  public boolean isEnabled() {
    return enabled;
  }

  public MultiTenancyCfg setEnabled(final boolean enabled) {
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
    final MultiTenancyCfg that = (MultiTenancyCfg) o;
    return enabled == that.enabled;
  }

  @Override
  public String toString() {
    return "MultiTenancyCfg{" + "enabled=" + enabled + '}';
  }
}
