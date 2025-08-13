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

public class KeyStore {
  private static final String PREFIX = "camunda.api.grpc.ssl.key-store";
  private static final Map<String, String> LEGACY_GATEWAY_KEY_STORE_PROPERTIES =
      Map.of(
          "filePath", "zeebe.gateway.security.keyStore.filePath",
          "password", "zeebe.gateway.security.keyStore.password");
  private static final Map<String, String> LEGACY_BROKER_KEY_STORE_PROPERTIES =
      Map.of(
          "filePath", "zeebe.broker.gateway.security.keyStore.filePath",
          "password", "zeebe.broker.gateway.security.keyStore.password");

  private Map<String, String> legacyPropertiesMap = LEGACY_BROKER_KEY_STORE_PROPERTIES;

  /** The path for keystore file */
  private File filePath;

  /** Sets the password for the keystore file, if not set it is assumed there is no password */
  private String password;

  public File getFilePath() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".file-path",
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
        PREFIX + ".password",
        password,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("password")));
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  @Override
  public KeyStore clone() {
    final KeyStore copy = new KeyStore();
    copy.filePath = filePath;
    copy.password = password;

    return copy;
  }

  public KeyStore withBrokerKeyStoreProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_BROKER_KEY_STORE_PROPERTIES;
    return copy;
  }

  public KeyStore withGatewayKeyStoreProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_GATEWAY_KEY_STORE_PROPERTIES;
    return copy;
  }
}
