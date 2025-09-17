/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.security.impl.AuthorizationChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AuthorizationCheckerConfiguration {
  @Bean
  public AuthorizationChecker authorizationChecker(final AuthorizationReader authorizationReader) {
    return new AuthorizationChecker(authorizationReader);
  }
}
