/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_TLS_ENABLED;

import java.io.File;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Ssl {

  /** Enables TLS authentication between clients and the gateway */
  private boolean enabled = DEFAULT_TLS_ENABLED;

  /** Sets the path to the certificate chain file */
  private File certificate;

  /** Sets the path to the private key file location */
  private File certificatePrivateKey;

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

  public File getCertificate() {
    return certificate;
  }

  public void setCertificate(final File certificate) {
    this.certificate = certificate;
  }

  public File getCertificatePrivateKey() {
    return certificatePrivateKey;
  }

  public void setCertificatePrivateKey(final File certificatePrivateKey) {
    this.certificatePrivateKey = certificatePrivateKey;
  }

  public KeyStore getKeyStore() {
    return keyStore;
  }

  public void setKeyStore(final KeyStore keyStore) {
    this.keyStore = keyStore;
  }

  @Override
  public Ssl clone() {
    final Ssl copy = new Ssl();
    copy.enabled = enabled;
    copy.certificate = certificate;
    copy.certificatePrivateKey = certificatePrivateKey;
    copy.keyStore = keyStore.clone();

    return copy;
  }
}
