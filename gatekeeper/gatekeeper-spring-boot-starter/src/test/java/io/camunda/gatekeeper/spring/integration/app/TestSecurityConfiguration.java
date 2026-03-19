/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.integration.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Security infrastructure beans that a real component would provide. In production these would
 * typically come from auto-configuration or a database-backed user store.
 */
@Configuration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  public UserDetailsService userDetailsService(final PasswordEncoder passwordEncoder) {
    final var demo =
        User.withUsername("demo").password(passwordEncoder.encode("demo")).roles("USER").build();
    final var operator =
        User.withUsername("operator")
            .password(passwordEncoder.encode("operator"))
            .roles("OPERATOR")
            .build();
    return new InMemoryUserDetailsManager(demo, operator);
  }
}
