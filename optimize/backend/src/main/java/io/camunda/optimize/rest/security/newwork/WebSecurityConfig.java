/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.newwork;

import static io.camunda.optimize.rest.security.newwork.OptimizeProfileService.AUTH_PROFILE;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Profile(AUTH_PROFILE)
@EnableWebSecurity
@Configuration
@Component("webSecurityConfig")
public class WebSecurityConfig extends BaseWebConfigurer {

  // protected OAuth2WebConfigurer oAuth2WebConfigurer;

  private final UserDetailsService userDetailsService;

  public WebSecurityConfig(
      final OptimizeProperties optimizeProperties,
      final OptimizeProfileService errorMessageService,
      // final OAuth2WebConfigurer oAuth2WebConfigurer,
      final UserDetailsService userDetailsService) {
    super(optimizeProperties, errorMessageService);
    // this.oAuth2WebConfigurer = oAuth2WebConfigurer;
    this.userDetailsService = userDetailsService;
  }

  @Override
  protected void applyAuthenticationSettings(final AuthenticationManagerBuilder builder)
      throws Exception {
    builder.userDetailsService(userDetailsService);
  }

  @Override
  protected void applyOAuth2Settings(final HttpSecurity http) throws Exception {
    // oAuth2WebConfigurer.configure(http);
  }
}
