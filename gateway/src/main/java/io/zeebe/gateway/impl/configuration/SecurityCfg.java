/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.configuration;

import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_TLS_ENABLED;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_CERTIFICATE_PATH;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_PRIVATE_KEY_PATH;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_SECURITY_ENABLED;

import io.zeebe.util.Environment;
import java.util.Objects;

public class SecurityCfg {

  private boolean enabled = DEFAULT_TLS_ENABLED;
  private String certificateChainPath;
  private String privateKeyPath;

  public void init(Environment environment) {
    environment.getBool(ENV_GATEWAY_SECURITY_ENABLED).ifPresent(this::setEnabled);
    environment.get(ENV_GATEWAY_CERTIFICATE_PATH).ifPresent(this::setCertificateChainPath);
    environment.get(ENV_GATEWAY_PRIVATE_KEY_PATH).ifPresent(this::setPrivateKeyPath);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public SecurityCfg setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public String getCertificateChainPath() {
    return certificateChainPath;
  }

  public SecurityCfg setCertificateChainPath(final String certificateChainPath) {
    this.certificateChainPath = certificateChainPath;
    return this;
  }

  public String getPrivateKeyPath() {
    return privateKeyPath;
  }

  public SecurityCfg setPrivateKeyPath(final String privateKeyPath) {
    this.privateKeyPath = privateKeyPath;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, certificateChainPath, privateKeyPath);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SecurityCfg that = (SecurityCfg) o;
    return enabled == that.enabled
        && Objects.equals(certificateChainPath, that.certificateChainPath)
        && Objects.equals(privateKeyPath, that.privateKeyPath);
  }

  @Override
  public String toString() {
    return "MonitoringCfg{"
        + "enabled="
        + enabled
        + ", certificateChainPath='"
        + certificateChainPath
        + "'"
        + ", privateKeyPath='"
        + privateKeyPath
        + "'}";
  }
}
