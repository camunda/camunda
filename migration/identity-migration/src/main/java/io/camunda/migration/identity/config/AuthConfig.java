/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import io.camunda.security.auth.Authentication;
import io.camunda.zeebe.auth.Authorization;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

  @Bean
  public Authentication servicesAuthentication() {
    return new Authentication.Builder()
        .claims(Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true))
        .build();
  }
}
