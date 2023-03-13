/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_TLS_ENABLED;

import java.io.File;
import java.util.Objects;

public final class SecurityCfg {

  private boolean enabled = DEFAULT_TLS_ENABLED;
  private File certificateChainPath;
  private File privateKeyPath;
  private AuthenticationCfg authentication = new AuthenticationCfg();

  public boolean isEnabled() {
    return enabled;
  }

  public SecurityCfg setEnabled(final boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public File getCertificateChainPath() {
    return certificateChainPath;
  }

  public SecurityCfg setCertificateChainPath(final File certificateChainPath) {
    this.certificateChainPath = certificateChainPath;
    return this;
  }

  public File getPrivateKeyPath() {
    return privateKeyPath;
  }

  public SecurityCfg setPrivateKeyPath(final File privateKeyPath) {
    this.privateKeyPath = privateKeyPath;
    return this;
  }

  public AuthenticationCfg getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final AuthenticationCfg authentication) {
    this.authentication = authentication;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, certificateChainPath, privateKeyPath, authentication);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SecurityCfg that = (SecurityCfg) o;
    return enabled == that.enabled
        && Objects.equals(certificateChainPath, that.certificateChainPath)
        && Objects.equals(privateKeyPath, that.privateKeyPath)
        && Objects.equals(authentication, that.authentication);
  }

  @Override
  public String toString() {
    return "SecurityCfg{"
        + "enabled="
        + enabled
        + ", certificateChainPath="
        + certificateChainPath
        + ", privateKeyPath="
        + privateKeyPath
        + ", authentication="
        + authentication
        + '}';
  }
}
