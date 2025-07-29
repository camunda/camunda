/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.authentication.DefaultCamundaAuthenticationProvider;
import io.camunda.authentication.handler.AuthFailureHandler;
import io.camunda.search.clients.auth.DisabledResourceAccessProvider;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provides beans that the WebSecurityConfig depends on. Implements the beans in a way that they do
 * not introduce dependencies to other modules, e.g. to the search layer.
 */
@Configuration
public class WebSecurityConfigTestContext {

  /**
   * @return REST controller with dummy endpoints for testing
   */
  @Bean
  public TestApiController createTestController() {
    return new TestApiController();
  }

  @Bean
  public UserDetailsService createUserDetailsService() {
    return new TestUserDetailsService();
  }

  @Bean
  public RoleServices createRoleServices() {
    return new RoleServices(null, null, null, null);
  }

  @Bean
  public GroupServices createGroupServices() {
    return new GroupServices(null, null, null, null);
  }

  @Bean
  public TenantServices createTenantServices() {
    return new TenantServices(null, null, null, null);
  }

  @Bean
  public CamundaAuthenticationProvider createCamundaAuthenticationProvider() {
    return new DefaultCamundaAuthenticationProvider(null, null);
  }

  @Bean
  public ResourceAccessProvider createResourceAccessProvider() {
    return new DisabledResourceAccessProvider();
  }

  /**
   * @return plain-text password encoder so that conversely we can use plain-text passwords in
   *     TestUserDetailsService
   */
  @Bean
  public PasswordEncoder createPasswordEncoder() {
    return NoOpPasswordEncoder.getInstance();
  }

  @Bean
  public AuthFailureHandler createFailureHandler() {
    return new AuthFailureHandler(new ObjectMapper());
  }

  /**
   * Replacing CustomMethodSecurityExpressionHandler which requires the search layer and is only
   * there to support hasPermission annotations on the old V1 APIs
   */
  @Bean
  public MethodSecurityExpressionHandler createMethodSecurityExpressionHandler() {
    return new DefaultMethodSecurityExpressionHandler();
  }

  /**
   * So that camunda.security properties can be used in tests; must be prefixed with
   * 'camunda.security' because this prefix is hardcoded in AuthenticationProperties.
   */
  @Bean
  @ConfigurationProperties("camunda.security")
  public SecurityConfiguration createSecurityConfiguration() {
    return new SecurityConfiguration();
  }
}
