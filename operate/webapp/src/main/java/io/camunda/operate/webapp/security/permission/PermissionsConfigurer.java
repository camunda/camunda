/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.permission;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.reader.ResourceAccessProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PermissionsConfigurer {

  @Bean
  public PermissionsService getPermissionsService(
      final SecurityConfiguration securityConfiguration,
      final AuthorizationChecker authorizationChecker,
      final ResourceAccessProvider resourceAccessProvider,
      final CamundaAuthenticationProvider authenticationProvider) {
    return new PermissionsService(
        securityConfiguration,
        authorizationChecker,
        resourceAccessProvider,
        authenticationProvider);
  }
}
