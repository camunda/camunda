/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.io.File;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class TlsCluster {

  /** Enables TLS authentication between this gateway and other nodes in the cluster */
  private boolean enabled = false;

  /** Sets the path to the certificate chain file */
  private File certificateChainPath;

  /** Sets the path to the private key file location */
  private File certificatePrivateKeyPath;

  /**
   * Configures the keystore file containing both the certificate chain and the private key.
   * Currently only supports PKCS12 format.
   */
  @NestedConfigurationProperty private KeyStore keyStore = new KeyStore();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public File getCertificateChainPath() {
    return certificateChainPath;
  }

  public void setCertificateChainPath(final File certificateChainPath) {
    this.certificateChainPath = certificateChainPath;
  }

  public File getCertificatePrivateKeyPath() {
    return certificatePrivateKeyPath;
  }

  public void setCertificatePrivateKeyPath(final File certificatePrivateKeyPath) {
    this.certificatePrivateKeyPath = certificatePrivateKeyPath;
  }

  public KeyStore getKeyStore() {
    return keyStore;
  }

  public void setKeyStore(final KeyStore keyStore) {
    this.keyStore = keyStore;
  }
}
