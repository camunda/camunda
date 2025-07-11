/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist;

import io.camunda.tasklist.webapp.security.AssigneeMigrator;
import io.camunda.tasklist.webapp.security.AssigneeMigratorNoImpl;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Temporary configuration required to start Tasklist as part of C8 single application.
 *
 * <p>Tasklist security package is excluded from the configuration of C8 single application to avoid
 * the conflicts with the existing Operate WebSecurity configuration. This will be solved after the
 * creation of a common Security layer.
 *
 * <p>For now, only default AUTH authentication is supported for Tasklist when run in C8 single
 * application.
 *
 * <p>TasklistSecurityStubsConfiguration provides the security related bean stubs required by the
 * service layer of Tasklist.
 */
@Configuration(proxyBeanMethods = false)
@Profile("tasklist & operate")
public class TasklistSecurityStubsConfiguration {
  @Bean
  public AssigneeMigrator stubAssigneeMigrator() {
    return new AssigneeMigratorNoImpl();
  }

  @Bean
  public TasklistProfileService stubTasklistProfileService() {
    return new TasklistProfileService() {

      @Override
      public String getMessageByProfileFor(final Exception exception) {
        return "";
      }

      @Override
      public boolean currentProfileCanLogout() {
        return true;
      }

      @Override
      public boolean isLoginDelegated() {
        return false;
      }
    };
  }
}
