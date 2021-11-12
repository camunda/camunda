/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.iam;

import static io.camunda.operate.webapp.security.OperateURIs.API;
import static io.camunda.operate.webapp.security.OperateURIs.AUTH_WHITELIST;
import static io.camunda.operate.webapp.security.OperateProfileService.IAM_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;

import io.camunda.iam.sdk.IamApi;
import io.camunda.iam.sdk.IamApiConfiguration;
import io.camunda.operate.property.IamProperties;
import io.camunda.operate.webapp.security.BaseWebConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.stereotype.Component;

@Profile(IAM_AUTH_PROFILE)
@Configuration
@EnableWebSecurity
@Component("webSecurityConfig")
public class IAMWebSecurityConfig extends BaseWebConfigurer {

  @Bean
  public IamApi iamApi() throws IllegalArgumentException {
    final IamProperties props = operateProperties.getIam();
    final IamApiConfiguration configuration =
        new IamApiConfiguration(props.getIssuerUrl(), props.getClientId(), props.getClientSecret());
    return new IamApi(configuration);
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.csrf().disable()
        .authorizeRequests()
        .antMatchers(AUTH_WHITELIST).permitAll()
        .antMatchers(API, ROOT).authenticated()
        .and().exceptionHandling()
        .authenticationEntryPoint(this::failureHandler);
  }

}
