/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist;

import io.camunda.tasklist.webapp.management.WebappManagementModuleConfiguration;
import io.camunda.tasklist.webapp.security.WebappSecurityModuleConfiguration;
import io.camunda.tasklist.webapp.security.identity.DefaultUserGroupService;
import io.camunda.tasklist.webapp.security.identity.UserGroupService;
import io.camunda.tasklist.webapp.security.identity.UserGroupServiceImpl;
import io.camunda.tasklist.zeebeimport.security.ImporterSecurityModuleConfiguration;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.gateway.Gateway;
import org.springframework.beans.factory.annotation.Autowired;
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

  public TasklistModuleConfiguration(
      @Autowired(required = false) final Broker broker,
      @Autowired(required = false) final Gateway gateway) {
    this.broker = broker;
    this.gateway = gateway;
  }

  /**
   * Bean definition for `UserGroupService` when the application is running under the
   * "consolidated-auth" profile.
   *
   * <p>- The `@Profile("consolidated-auth")` annotation ensures that this bean is only loaded when
   * the profile "consolidated-auth" is active.
   *
   * <p>This bean provides the **main authorization logic** when consolidated authentication is
   * enabled.
   */
  @Bean
  @Primary
  @Profile("consolidated-auth")
  public UserGroupService consolidatedUserGroupService() {
    return new UserGroupServiceImpl();
  }

  /**
   * Fallback bean for `UserGroupService`, used when the "consolidated-auth" profile is **not
   * active**.
   *
   * <p>- This bean ensures that authorization is always available, even when consolidated
   * authentication is not enabled. - It provides a **default, full-access authorization service**.
   *
   * <p>This bean declaration can be removed after the consolidated authentication is fully
   * implemented
   */
  @Bean
  @Profile("!consolidated-auth")
  public UserGroupService defaultUserGroupService() {
    return new DefaultUserGroupService();
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
