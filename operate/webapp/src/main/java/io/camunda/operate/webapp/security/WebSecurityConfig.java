/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import static io.camunda.operate.OperateProfileService.AUTH_PROFILE;

import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Autowired protected OAuth2WebConfigurer oAuth2WebConfigurer;

  @Autowired private UserDetailsService userDetailsService;

  @Override
  protected void applyAuthenticationSettings(final AuthenticationManagerBuilder builder)
      throws Exception {
    builder.userDetailsService(userDetailsService);
  }

  @Override
  protected void applyOAuth2Settings(final HttpSecurity http) throws Exception {
    oAuth2WebConfigurer.configure(http);
  }
}
