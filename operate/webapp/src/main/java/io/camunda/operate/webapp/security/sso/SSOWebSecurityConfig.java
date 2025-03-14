/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.sso;

import static io.camunda.operate.OperateProfileService.SSO_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.API;
import static io.camunda.operate.webapp.security.OperateURIs.AUTH_WHITELIST;
import static io.camunda.operate.webapp.security.OperateURIs.PUBLIC_API;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;

import com.auth0.AuthenticationController;
import io.camunda.operate.OperateProfileService;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.webapp.security.BaseWebConfigurer;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.stereotype.Component;

@Profile(SSO_AUTH_PROFILE)
@Configuration
@EnableWebSecurity
@Component("webSecurityConfig")
public class SSOWebSecurityConfig extends BaseWebConfigurer {

  protected OAuth2WebConfigurer oAuth2WebConfigurer;

  public SSOWebSecurityConfig(
      final OperateProperties operateProperties,
      final OperateProfileService errorMessageService,
      final OAuth2WebConfigurer oAuth2WebConfigurer) {
    super(operateProperties, errorMessageService);
    this.oAuth2WebConfigurer = oAuth2WebConfigurer;
  }

  @Bean
  public AuthenticationController authenticationController() {
    return AuthenticationController.newBuilder(
            operateProperties.getAuth0().getDomain(),
            operateProperties.getAuth0().getClientId(),
            operateProperties.getAuth0().getClientSecret())
        .build();
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
                  .requestMatchers(API, PUBLIC_API, ROOT + "**")
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
