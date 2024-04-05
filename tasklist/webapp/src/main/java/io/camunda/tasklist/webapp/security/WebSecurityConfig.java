/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.AUTH_PROFILE;

import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
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

  @Autowired private UserDetailsService userDetailsService;

  @Autowired private OAuth2WebConfigurer oAuth2WebConfigurer;

  @Override
  protected void applyAuthenticationSettings(AuthenticationManagerBuilder builder)
      throws Exception {
    builder.userDetailsService(userDetailsService);
  }

  @Override
  protected void applyOAuth2Settings(HttpSecurity http) throws Exception {
    oAuth2WebConfigurer.configure(http);
  }
}
