/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_TLS_ENABLED;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.io.File;
import java.util.Map;
import java.util.Set;

public class Ssl {
  private static final String PREFIX = "camunda.api.grpc.ssl";
  private static final Map<String, String> LEGACY_GATEWAY_SSL_PROPERTIES =
      Map.of(
          "enabled", "zeebe.gateway.security.enabled",
          "certificateChainPath", "zeebe.gateway.security.certificateChainPath",
          "privateKeyPath", "zeebe.gateway.security.privateKeyPath");
  private static final Map<String, String> LEGACY_BROKER_SSL_PROPERTIES =
      Map.of(
          "enabled", "zeebe.broker.gateway.security.enabled",
          "certificateChainPath", "zeebe.broker.gateway.security.certificateChainPath",
          "privateKeyPath", "zeebe.broker.gateway.security.privateKeyPath");

  private Map<String, String> legacyPropertiesMap = LEGACY_BROKER_SSL_PROPERTIES;

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
  private KeyStore keyStore = new KeyStore();

  public boolean isEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enabled",
        enabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("enabled")));
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public File getCertificate() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".certificate",
        certificate,
        File.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("certificateChainPath")));
  }

  public void setCertificate(final File certificate) {
    this.certificate = certificate;
  }

  public File getCertificatePrivateKey() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".certificate-private-key",
        certificatePrivateKey,
        File.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("privateKeyPath")));
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

  public Ssl withBrokerSslProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_BROKER_SSL_PROPERTIES;
    return copy;
  }

  public Ssl withGatewaySslProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_GATEWAY_SSL_PROPERTIES;
    return copy;
  }
}
