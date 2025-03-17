/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.API;
import static io.camunda.operate.webapp.security.OperateURIs.AUTH_WHITELIST;
import static io.camunda.operate.webapp.security.OperateURIs.PUBLIC_API;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;

import io.camunda.operate.OperateProfileService;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.webapp.security.BaseWebConfigurer;
import io.camunda.operate.webapp.security.oauth2.IdentityOAuth2WebConfigurer;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.stereotype.Component;

@Profile(IDENTITY_AUTH_PROFILE)
@EnableWebSecurity
@Component("webSecurityConfig")
public class IdentityWebSecurityConfig extends BaseWebConfigurer {

  protected IdentityOAuth2WebConfigurer oAuth2WebConfigurer;

  public IdentityWebSecurityConfig(
      final OperateProperties operateProperties,
      final OperateProfileService errorMessageService,
      final IdentityOAuth2WebConfigurer oAuth2WebConfigurer) {
    super(operateProperties, errorMessageService);
    this.oAuth2WebConfigurer = oAuth2WebConfigurer;
  }

  @Override
  protected void applySecurityFilterSettings(final HttpSecurity http) throws Exception {
    if (operateProperties.isCsrfPreventionEnabled()) {
      logger.info("CSRF Protection enabled");
      configureCSRF(http);
    } else {
      http.csrf((csrf) -> csrf.disable());
    }
    http.authorizeRequests(
            (authorize) -> {
              authorize
                  .requestMatchers(AUTH_WHITELIST)
                  .permitAll()
                  .requestMatchers(API, PUBLIC_API, ROOT)
                  .authenticated();
            })
        .exceptionHandling(
            (handling) -> {
              handling.authenticationEntryPoint(this::failureHandler);
            });
  }

  @Override
  protected void applyOAuth2Settings(final HttpSecurity http) throws Exception {
    oAuth2WebConfigurer.configure(http);
  }
}
