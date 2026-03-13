/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.autoconfigure;

import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import io.camunda.gatekeeper.spi.CamundaAuthenticationConverter;
import io.camunda.gatekeeper.spi.MembershipResolver;
import io.camunda.gatekeeper.spring.condition.ConditionalOnAuthenticationMethod;
import io.camunda.gatekeeper.spring.converter.UsernamePasswordAuthenticationTokenConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;

/**
 * Auto-configuration for basic-auth-based authentication. Only active when the authentication
 * method is set to BASIC.
 */
@AutoConfiguration(after = GatekeeperAuthAutoConfiguration.class)
@ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
public final class GatekeeperBasicAuthAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public CamundaAuthenticationConverter<Authentication>
      usernamePasswordAuthenticationTokenConverter(final MembershipResolver membershipResolver) {
    return new UsernamePasswordAuthenticationTokenConverter(membershipResolver);
  }
}
