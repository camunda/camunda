/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.io.File;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class TlsCluster implements Cloneable {
  private static final String PREFIX = "camunda.security.transport-layer-security.cluster";

  private static final Map<String, String> LEGACY_BROKER_NETWORK_SECURITY_PROPERTIES =
      Map.of(
          "enabled",
          "zeebe.broker.network.security.enabled",
          "certificateChainPath",
          "zeebe.broker.network.security.certificateChainPath",
          "certificatePrivateKeyPath",
          "zeebe.broker.network.security.privateKeyPath");

  private static final Map<String, String> LEGACY_GATEWAY_CLUSTER_SECURITY_PROPERTIES =
      Map.of(
          "enabled",
          "zeebe.gateway.cluster.security.enabled",
          "certificateChainPath",
          "zeebe.gateway.cluster.security.certificateChainPath",
          "certificatePrivateKeyPath",
          "zeebe.gateway.cluster.security.privateKeyPath");

  private Map<String, String> legacyPropertiesMap = LEGACY_BROKER_NETWORK_SECURITY_PROPERTIES;

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

  public File getCertificateChainPath() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".certificate-chain-path",
        certificateChainPath,
        File.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("certificateChainPath")));
  }

  public void setCertificateChainPath(final File certificateChainPath) {
    this.certificateChainPath = certificateChainPath;
  }

  public File getCertificatePrivateKeyPath() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".certificate-private-key-path",
        certificatePrivateKeyPath,
        File.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("certificatePrivateKeyPath")));
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

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch (final CloneNotSupportedException e) {
      throw new AssertionError("Unexpected: Class must implement Cloneable", e);
    }
  }

  public TlsCluster withBrokerTlsClusterProperties() {
    final var copy = (TlsCluster) clone();
    copy.legacyPropertiesMap = LEGACY_BROKER_NETWORK_SECURITY_PROPERTIES;
    return copy;
  }

  public TlsCluster withGatewayTlsClusterProperties() {
    final var copy = (TlsCluster) clone();
    copy.legacyPropertiesMap = LEGACY_GATEWAY_CLUSTER_SECURITY_PROPERTIES;
    return copy;
  }
}
