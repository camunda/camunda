/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import java.util.Objects;

public final class AuthenticationCfg {
  private AuthMode mode = AuthMode.NONE;

  public AuthMode getMode() {
    return mode;
  }

  public void setMode(final AuthMode mode) {
    this.mode = mode;
  }

  @Override
  public int hashCode() {
    return Objects.hash(mode);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AuthenticationCfg that = (AuthenticationCfg) o;
    return mode == that.mode;
  }

  @Override
  public String toString() {
    return "AuthenticationCfg{" + "mode=" + mode + '}';
  }

  public enum AuthMode {
    NONE,
    IDENTITY,
  }
}
