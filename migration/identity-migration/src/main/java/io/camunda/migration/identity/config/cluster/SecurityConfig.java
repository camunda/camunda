/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config.cluster;

import java.io.File;
import java.util.Objects;

public final class SecurityConfig {
  public static final boolean DEFAULT_TLS_ENABLED = false;

  private boolean enabled = DEFAULT_TLS_ENABLED;
  private File certificateChainPath;
  private File privateKeyPath;
  private KeyStoreConfig keyStore = new KeyStoreConfig();

  public boolean isEnabled() {
    return enabled;
  }

  public SecurityConfig setEnabled(final boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public File getCertificateChainPath() {
    return certificateChainPath;
  }

  public SecurityConfig setCertificateChainPath(final File certificateChainPath) {
    this.certificateChainPath = certificateChainPath;
    return this;
  }

  public File getPrivateKeyPath() {
    return privateKeyPath;
  }

  public SecurityConfig setPrivateKeyPath(final File privateKeyPath) {
    this.privateKeyPath = privateKeyPath;
    return this;
  }

  public KeyStoreConfig getKeyStore() {
    return keyStore;
  }

  public void setKeyStore(final KeyStoreConfig keyStore) {
    this.keyStore = keyStore;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, certificateChainPath, privateKeyPath, keyStore);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SecurityConfig that = (SecurityConfig) o;
    return enabled == that.enabled
        && Objects.equals(certificateChainPath, that.certificateChainPath)
        && Objects.equals(privateKeyPath, that.privateKeyPath)
        && Objects.equals(keyStore, that.keyStore);
  }

  @Override
  public String toString() {
    return "SecurityConfig{"
        + "enabled="
        + enabled
        + ", certificateChainPath="
        + certificateChainPath
        + ", privateKeyPath="
        + privateKeyPath
        + ", keyStore="
        + keyStore
        + '}';
  }
}
