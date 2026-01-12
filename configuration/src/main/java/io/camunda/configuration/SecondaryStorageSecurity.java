/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;

public class SecondaryStorageSecurity {

  private final String databaseName;

  /** Enable security */
  private boolean enabled = false;

  /** Path to certificate used by Elasticsearch or Opensearch */
  private String certificatePath;

  /** Should the hostname be validated */
  private boolean verifyHostname = true;

  /** Certificate was self-signed */
  private boolean selfSigned = false;

  public SecondaryStorageSecurity(final String databaseName) {
    this.databaseName = databaseName.toLowerCase();
  }

  public boolean isEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".enabled",
        enabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyEnabledProperties());
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getCertificatePath() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".certificate-path",
        certificatePath,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyCertificatePathProperties());
  }

  public void setCertificatePath(final String certificatePath) {
    this.certificatePath = certificatePath;
  }

  public boolean isVerifyHostname() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".verify-hostname",
        verifyHostname,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyVerifyHostnameProperties());
  }

  public void setVerifyHostname(final boolean verifyHostname) {
    this.verifyHostname = verifyHostname;
  }

  public boolean isSelfSigned() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".self-signed",
        selfSigned,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacySelfSignedProperties());
  }

  public void setSelfSigned(final boolean selfSigned) {
    this.selfSigned = selfSigned;
  }

  private String prefix() {
    return "camunda.data.secondary-storage." + databaseName + ".security";
  }

  private Set<String> legacyEnabledProperties() {
    return Set.of(
        "camunda.database.security.enabled",
        "zeebe.broker.exporters.camundaexporter.args.connect.security.enabled");
  }

  private Set<String> legacyCertificatePathProperties() {
    return Set.of(
        "camunda.database.security.certificatePath",
        "camunda.tasklist." + databaseName + ".ssl.certificatePath",
        "camunda.operate." + databaseName + ".ssl.certificatePath",
        "zeebe.broker.exporters.camundaexporter.args.connect.security.certificatePath");
  }

  private Set<String> legacyVerifyHostnameProperties() {
    return Set.of(
        "camunda.database.security.verifyHostname",
        "camunda.tasklist." + databaseName + ".ssl.verifyHostname",
        "camunda.operate." + databaseName + ".ssl.verifyHostname",
        "zeebe.broker.exporters.camundaexporter.args.connect.security.verifyHostname");
  }

  private Set<String> legacySelfSignedProperties() {
    return Set.of(
        "camunda.database.security.selfSigned",
        "camunda.tasklist." + databaseName + ".ssl.selfSigned",
        "camunda.operate." + databaseName + ".ssl.selfSigned",
        "zeebe.broker.exporters.camundaexporter.args.connect.security.selfSigned");
  }
}
