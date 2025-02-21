/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist;

import static io.camunda.tasklist.property.IdentityProperties.FULL_GROUP_ACCESS;

import io.camunda.authentication.service.CamundaUserService;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.tasklist.webapp.management.WebappManagementModuleConfiguration;
import io.camunda.tasklist.webapp.security.WebappSecurityModuleConfiguration;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorizationService;
import io.camunda.tasklist.zeebeimport.security.ImporterSecurityModuleConfiguration;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.gateway.Gateway;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Entry point for the Tasklist modules by using the the {@link
 * io.camunda.application.Profile#TASKLIST} profile, so that the appropriate Tasklist application
 * properties are applied.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = "io.camunda.tasklist",
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.zeebeimport\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.webapp\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.archiver\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.data\\..*"),
    },
    // use fully qualified names as bean name, as we have classes with same names for different
    // versions of importer
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Profile("tasklist")
public class TasklistModuleConfiguration {
  // if present, then it will ensure
  // that the broker is started first
  private final Broker broker;

  // if present, then it will ensure
  // that the gateway is started first
  private final Gateway gateway;

  @Autowired private SecurityConfiguration securityConfiguration;

  public TasklistModuleConfiguration(
      @Autowired(required = false) final Broker broker,
      @Autowired(required = false) final Gateway gateway) {
    this.broker = broker;
    this.gateway = gateway;
  }

  @Bean
  @Primary
  @ConditionalOnProperty(name = "security.auth.method", havingValue = "consolidated-auth")
  public IdentityAuthorizationService consolidatedIdentityAuthorizationService(
      final CamundaUserService camundaUserService) {
    return () -> camundaUserService.getCurrentUser().groups();
  }

  // In case the consolidated auth method is not set, we use the default identity authorization
  // Authorization is only supported using the consolidated auth method
  @Bean
  @Primary
  public IdentityAuthorizationService defaultIdentityAuthorizationService() {
    return () -> List.of(FULL_GROUP_ACCESS);
  }

  @Configuration(proxyBeanMethods = false)
  @Import({WebappSecurityModuleConfiguration.class, ImporterSecurityModuleConfiguration.class})
  @Profile("!operate")
  public static class TasklistSecurityModulesConfiguration {}

  @Configuration(proxyBeanMethods = false)
  @Import(WebappManagementModuleConfiguration.class)
  @Profile("!operate")
  public static class TasklistManagementModulesConfiguration {}
}
