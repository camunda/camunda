/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import io.camunda.application.commons.service.CamundaLicenseConfiguration.LicenseKeyProperties;
import io.camunda.service.CamundaServices;
import io.camunda.service.ManagementService;
import io.camunda.service.license.CamundaLicense;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LicenseKeyProperties.class)
public class CamundaLicenseConfiguration {
  private final LicenseKeyProperties licenseKeyProperties;

  public CamundaLicenseConfiguration(final LicenseKeyProperties licenseKeyProperties) {
    this.licenseKeyProperties = licenseKeyProperties;
  }

  @Bean
  public ManagementService managementService(
      final CamundaServices camundaServices, final CamundaLicense camundaLicense) {
    final var managementService = camundaServices.managementService().withLicense(camundaLicense);
    // trigger to log if license is valid or not during startup
    managementService.isCamundaLicenseValid();
    return managementService;
  }

  @Bean
  public CamundaLicense camundaLicense() {
    final String license = licenseKeyProperties.key();
    return new CamundaLicense(license);
  }

  @ConfigurationProperties("camunda.license")
  public record LicenseKeyProperties(String key) {}
}
