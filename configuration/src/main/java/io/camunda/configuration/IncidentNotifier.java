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

public class IncidentNotifier {

  private final String databaseName;

  private String webhook;

  /** Defines the domain which the user always sees */
  private String auth0Domain;

  private String auth0Protocol = "https";

  private String m2mClientId;

  private String m2mClientSecret;

  private String m2mAudience;

  public IncidentNotifier(final String databaseName) {
    this.databaseName = databaseName.toLowerCase();
  }

  public String getWebhook() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".webhook",
        webhook,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        legacyWebhookProperties());
  }

  public void setWebhook(final String webhook) {
    this.webhook = webhook;
  }

  public String getAuth0Domain() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".auth0-domain",
        auth0Domain,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        legacyAuth0DomainProperties());
  }

  public void setAuth0Domain(final String auth0Domain) {
    this.auth0Domain = auth0Domain;
  }

  public String getAuth0Protocol() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".auth0-protocol",
        auth0Protocol,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        legacyAuth0ProtocolProperties());
  }

  public void setAuth0Protocol(final String auth0Protocol) {
    this.auth0Protocol = auth0Protocol;
  }

  public String getM2mClientId() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".m2m-client-id",
        m2mClientId,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        legacyM2mClientIdProperties());
  }

  public void setM2mClientId(final String m2mClientId) {
    this.m2mClientId = m2mClientId;
  }

  public String getM2mClientSecret() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".m2m-client-secret",
        m2mClientSecret,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        legacyM2mClientSecretProperties());
  }

  public void setM2mClientSecret(final String m2mClientSecret) {
    this.m2mClientSecret = m2mClientSecret;
  }

  public String getM2mAudience() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".m2m-audience",
        m2mAudience,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        legacyM2mAudienceProperties());
  }

  public void setM2mAudience(final String m2mAudience) {
    this.m2mAudience = m2mAudience;
  }

  private String prefix() {
    return "camunda.data.secondary-storage." + databaseName + ".incident-notifier";
  }

  private Set<String> legacyWebhookProperties() {
    return Set.of("zeebe.broker.exporters.camundaexporter.args.notifier.webhook");
  }

  private Set<String> legacyAuth0DomainProperties() {
    return Set.of("zeebe.broker.exporters.camundaexporter.args.notifier.auth0Domain");
  }

  private Set<String> legacyAuth0ProtocolProperties() {
    return Set.of("zeebe.broker.exporters.camundaexporter.args.notifier.auth0Protocol");
  }

  private Set<String> legacyM2mClientIdProperties() {
    return Set.of("zeebe.broker.exporters.camundaexporter.args.notifier.m2mClientId");
  }

  private Set<String> legacyM2mClientSecretProperties() {
    return Set.of("zeebe.broker.exporters.camundaexporter.args.notifier.m2mClientSecret");
  }

  private Set<String> legacyM2mAudienceProperties() {
    return Set.of("zeebe.broker.exporters.camundaexporter.args.notifier.m2mAudience");
  }
}
