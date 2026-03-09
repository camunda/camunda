/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.search.clients.reader.UserReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Registers the {@link CamundaUserDetailsService} for BASIC auth form login and enables Spring
 * Security. {@code @EnableWebSecurity} must be on this component-scanned class (not only on the
 * auth library's auto-configuration) so that Spring Security initializes early enough for the
 * {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider} to discover the
 * {@link UserDetailsService} bean.
 */
@Configuration(proxyBeanMethods = false)
@Profile("consolidated-auth")
@ConditionalOnAnyHttpGatewayEnabled
@EnableWebSecurity
public class AuthenticationConfiguration {

  @Bean
  @ConditionalOnMissingBean(UserDetailsService.class)
  public CamundaUserDetailsService camundaUserDetailsService(final UserReader userReader) {
    return new CamundaUserDetailsService(userReader);
  }
}
