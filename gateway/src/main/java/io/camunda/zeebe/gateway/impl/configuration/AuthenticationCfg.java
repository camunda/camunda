/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import java.util.Objects;

public final class AuthenticationCfg {
  private AuthMode mode = AuthMode.NONE;
  private IdentityCfg identity = new IdentityCfg();

  public AuthMode getMode() {
    return mode;
  }

  public void setMode(final AuthMode mode) {
    this.mode = mode;
  }

  public IdentityCfg getIdentity() {
    return identity;
  }

  public void setIdentity(final IdentityCfg identity) {
    this.identity = identity;
  }

  @Override
  public int hashCode() {
    return Objects.hash(mode, identity);
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
    return mode == that.mode && Objects.equals(identity, that.identity);
  }

  @Override
  public String toString() {
    return "AuthenticationCfg{" + "mode=" + mode + ", identity=" + identity + '}';
  }

  public enum AuthMode {
    NONE,
    IDENTITY,
  }
}
