/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.application.commons.service;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.service.ManagementServices;
import io.camunda.service.license.CamundaLicense;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ManagementServicesConfiguration {

  private final UnifiedConfiguration unifiedConfiguration;

  public ManagementServicesConfiguration(final UnifiedConfiguration unifiedConfiguration) {
    this.unifiedConfiguration = unifiedConfiguration;
  }

  @Bean
  public ManagementServices managementServices() {
    return new ManagementServices(camundaLicense());
  }

  @Bean
  public CamundaLicense camundaLicense() {
    return new CamundaLicense(unifiedConfiguration.getCamunda().getLicense().getKey());
  }
}
