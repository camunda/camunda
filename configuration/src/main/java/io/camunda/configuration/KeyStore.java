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

public class KeyStore implements Cloneable {
  private static final Map<String, String> LEGACY_GATEWAY_KEY_STORE_PROPERTIES =
      Map.of(
          "filePath", "zeebe.gateway.security.keyStore.filePath",
          "password", "zeebe.gateway.security.keyStore.password");
  private static final Map<String, String> LEGACY_BROKER_KEY_STORE_PROPERTIES =
      Map.of(
          "filePath", "zeebe.broker.gateway.security.keyStore.filePath",
          "password", "zeebe.broker.gateway.security.keyStore.password");
  private static final Map<String, String> LEGACY_BROKER_NETWORK_SECURITY_KEY_STORE_PROPERTIES =
      Map.of(
          "filePath", "zeebe.broker.network.security.keyStore.filePath",
          "password", "zeebe.broker.network.security.keyStore.password");
  private static final Map<String, String> LEGACY_GATEWAY_CLUSTER_SECURITY_KEY_STORE_PROPERTIES =
      Map.of(
          "filePath", "zeebe.gateway.cluster.security.keyStore.filePath",
          "password", "zeebe.gateway.cluster.security.keyStore.password");

  private String prefix = "camunda.api.grpc.ssl.key-store";
  private Map<String, String> legacyPropertiesMap = LEGACY_BROKER_KEY_STORE_PROPERTIES;

  /** The path for keystore file */
  private File filePath;

  /** Sets the password for the keystore file, if not set it is assumed there is no password */
  private String password;

  public File getFilePath() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".file-path",
        filePath,
        File.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("filePath")));
  }

  public void setFilePath(final File filePath) {
    this.filePath = filePath;
  }

  public String getPassword() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".password",
        password,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("password")));
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch (final CloneNotSupportedException e) {
      throw new AssertionError("Unexpected: Class must implement Cloneable", e);
    }
  }

  public KeyStore withBrokerKeyStoreProperties() {
    final var copy = (KeyStore) clone();
    copy.prefix = "camunda.api.grpc.ssl.key-store";
    copy.legacyPropertiesMap = LEGACY_BROKER_KEY_STORE_PROPERTIES;

    return copy;
  }

  public KeyStore withGatewayKeyStoreProperties() {
    final var copy = (KeyStore) clone();
    copy.prefix = "camunda.api.grpc.ssl.key-store";
    copy.legacyPropertiesMap = LEGACY_GATEWAY_KEY_STORE_PROPERTIES;
    return copy;
  }

  public KeyStore withBrokerTlsClusterKeyStoreProperties() {
    final var copy = (KeyStore) clone();
    copy.prefix = "camunda.security.transport-layer-security.cluster.key-store";
    copy.legacyPropertiesMap = LEGACY_BROKER_NETWORK_SECURITY_KEY_STORE_PROPERTIES;
    return copy;
  }

  public KeyStore withGatewayTlsClusterKeyStoreProperties() {
    final var copy = (KeyStore) clone();
    copy.prefix = "camunda.security.transport-layer-security.cluster.key-store";
    copy.legacyPropertiesMap = LEGACY_GATEWAY_CLUSTER_SECURITY_KEY_STORE_PROPERTIES;
    return copy;
  }
}
