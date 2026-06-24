/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.exception.BasicAuthenticationNotSupportedException;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.spring.annotation.ConditionalOnAuthenticationMethod;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides fail-fast behavior when Basic Authentication is configured but secondary storage is
 * disabled (camunda.database.type=none). This prevents misleading security flows and produces a
 * clear error message at context startup.
 */
@Configuration
@ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
@ConditionalOnSecondaryStorageDisabled
public class BasicAuthenticationNoDbConfiguration {

  @Bean
  public BasicAuthenticationNoDbFailFastBean basicAuthenticationNoDbFailFastBean() {
    throw new BasicAuthenticationNotSupportedException();
  }

  /** Marker bean for Basic Auth no-db fail-fast configuration. */
  public static class BasicAuthenticationNoDbFailFastBean {}
}
