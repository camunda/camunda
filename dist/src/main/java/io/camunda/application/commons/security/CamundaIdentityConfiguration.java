/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.security.configuration.EngineIdpAssignmentConfiguration;
import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CamundaIdentityConfiguration.CamundaIdentityProperties.class)
public class CamundaIdentityConfiguration {

  @Bean
  public PhysicalTenantIdpRegistry physicalTenantIdpRegistry(
      final CamundaIdentityProperties properties) {
    return new PhysicalTenantIdpRegistry(properties.getEngineIdpAssignments());
  }

  @ConfigurationProperties("camunda.identity")
  public static final class CamundaIdentityProperties extends EngineIdpAssignmentConfiguration {}
}
