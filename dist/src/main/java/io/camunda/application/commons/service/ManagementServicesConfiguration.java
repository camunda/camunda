/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import io.camunda.application.commons.service.ManagementServicesConfiguration.ClientModeProperty;
import io.camunda.application.commons.service.ManagementServicesConfiguration.LicenseKeyProperties;
import io.camunda.service.ManagementServices;
import io.camunda.service.license.CamundaLicense;
import io.camunda.service.license.SaaSLicense;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({LicenseKeyProperties.class, ClientModeProperty.class})
public class ManagementServicesConfiguration {
  private static final String SELF_MANAGED_MODE = "self-managed";
  private final LicenseKeyProperties licenseKeyProperties;
  private final ClientModeProperty clientModeProperty;

  @Autowired
  public ManagementServicesConfiguration(
      final LicenseKeyProperties licenseKeyProperties,
      final ClientModeProperty clientModeProperty) {
    this.licenseKeyProperties = licenseKeyProperties;
    this.clientModeProperty = clientModeProperty;
  }

  @Bean
  public ManagementServices managementService(final CamundaLicense camundaLicense) {
    final ManagementServices managementServices =
        new ManagementServices().withLicense(camundaLicense);
    // trigger to log if license is valid or not during startup
    managementServices.isCamundaLicenseValid();

    return managementServices;
  }

  @Bean
  public CamundaLicense camundaLicense() {
    final String clientMode = clientModeProperty.key();
    if (SELF_MANAGED_MODE.equalsIgnoreCase(clientMode)) {
      final String license = licenseKeyProperties.key();
      return new CamundaLicense(license);
    } else {
      return new SaaSLicense();
    }
  }

  @ConfigurationProperties("camunda.license")
  public record LicenseKeyProperties(String key) {}

  @ConfigurationProperties("camunda.client.mode")
  public record ClientModeProperty(String key) {}
}
