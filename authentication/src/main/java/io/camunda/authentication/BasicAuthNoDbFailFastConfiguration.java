/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.gatekeeper.exception.BasicAuthNotSupportedException;
import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import io.camunda.gatekeeper.spring.condition.ConditionalOnAuthenticationMethod;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fail-fast configuration that prevents startup when Basic Authentication is configured but
 * secondary storage is disabled. Basic auth requires user data from secondary storage.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
@ConditionalOnSecondaryStorageDisabled
public final class BasicAuthNoDbFailFastConfiguration {

  @Bean
  public Object basicAuthNoDbFailFastBean() {
    throw new BasicAuthNotSupportedException();
  }
}
