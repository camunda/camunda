/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.configuration.legacy;

import static io.zeebe.gateway.impl.configuration.legacy.ConfigurationDefaults.DEFAULT_TLS_ENABLED;
import static io.zeebe.gateway.impl.configuration.legacy.EnvironmentConstants.ENV_GATEWAY_CERTIFICATE_PATH;
import static io.zeebe.gateway.impl.configuration.legacy.EnvironmentConstants.ENV_GATEWAY_PRIVATE_KEY_PATH;
import static io.zeebe.gateway.impl.configuration.legacy.EnvironmentConstants.ENV_GATEWAY_SECURITY_ENABLED;

import io.zeebe.util.Environment;
import java.util.Objects;

@Deprecated(since = "0.23.0-alpha1")
/* Kept in order to be able to offer a migration path for old configuration.
 * It is not yet clear whether we intent to offer a migration path for old configurations.
 * This class might be moved or removed on short notice.
 */
public final class SecurityCfg {

  private boolean enabled = DEFAULT_TLS_ENABLED;
  private String certificateChainPath;
  private String privateKeyPath;

  public void init(final Environment environment) {
    environment.getBool(ENV_GATEWAY_SECURITY_ENABLED).ifPresent(this::setEnabled);
    environment.get(ENV_GATEWAY_CERTIFICATE_PATH).ifPresent(this::setCertificateChainPath);
    environment.get(ENV_GATEWAY_PRIVATE_KEY_PATH).ifPresent(this::setPrivateKeyPath);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public SecurityCfg setEnabled(final boolean enabled) {
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
