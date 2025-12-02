/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.zeebe.backup.azure.SasTokenConfig;
import java.util.LinkedHashSet;
import java.util.Set;

public class SasToken {
  private static final String PREFIX = "camunda.data.primary-storage.backup.azure.sas-token";
  private static final Set<Set<String>> LEGACY_SASTOKEN_TYPE_PROPERTIES = new LinkedHashSet<>(2);
  private static final Set<Set<String>> LEGACY_SASTOKEN_VALUE_PROPERTIES = new LinkedHashSet<>(2);

  static {
    LEGACY_SASTOKEN_TYPE_PROPERTIES.add(Set.of("zeebe.broker.data.backup.azure.sasToken.type"));
    LEGACY_SASTOKEN_TYPE_PROPERTIES.add(Set.of("camunda.data.backup.azure.sas-token.type"));

    LEGACY_SASTOKEN_VALUE_PROPERTIES.add(Set.of("zeebe.broker.data.backup.azure.sasToken.value"));
    LEGACY_SASTOKEN_VALUE_PROPERTIES.add(Set.of("camunda.data.backup.azure.sas-token.value"));
  }

  /** The SAS token must be of the following types: "delegation", "service" or "account". */
  private SasTokenType type;

  /** Specifies the key value of the SAS token. */
  private String value;

  public static SasToken fromSasTokenConfig(final SasTokenConfig sasTokenConfig) {
    final var sasToken = new SasToken();
    sasToken.setType(SasToken.SasTokenType.valueOf(sasTokenConfig.type().name()));
    sasToken.setValue(sasTokenConfig.value());
    return sasToken;
  }

  public SasTokenType getType() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".type",
        type,
        SasTokenType.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_SASTOKEN_TYPE_PROPERTIES);
  }

  public void setType(final SasTokenType type) {
    this.type = type;
  }

  public String getValue() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".value",
        value,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_SASTOKEN_VALUE_PROPERTIES);
  }

  public void setValue(final String value) {
    this.value = value;
  }

  public SasTokenConfig toSasTokenConfig() {
    if (getType() == null || getValue() == null) {
      return null;
    }
    return new SasTokenConfig.Builder()
        .withValue(getValue())
        .withTokenType(io.camunda.zeebe.backup.azure.SasTokenType.valueOf(getType().name()))
        .build();
  }

  public enum SasTokenType {
    DELEGATION,
    SERVICE,
    ACCOUNT;
  }
}
