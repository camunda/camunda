/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.service.UserServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"io.camunda.authentication"})
@ConfigurationPropertiesScan(basePackages = {"io.camunda.authentication"})
@Profile("consolidated-auth")
@ConditionalOnAnyHttpGatewayEnabled
public class AuthenticationConfiguration {

  @Bean
  @ConditionalOnMissingBean(UserDetailsService.class)
  public CamundaUserDetailsService camundaUserDetailsService(final UserServices userServices) {
    return new CamundaUserDetailsService(userServices);
  }
}
