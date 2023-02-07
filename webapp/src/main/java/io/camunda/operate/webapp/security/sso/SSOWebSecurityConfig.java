/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.sso;

import static io.camunda.operate.webapp.security.OperateURIs.API;
import static io.camunda.operate.webapp.security.OperateURIs.AUTH_WHITELIST;
import static io.camunda.operate.webapp.security.OperateURIs.PUBLIC_API;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;
import static io.camunda.operate.webapp.security.OperateProfileService.SSO_AUTH_PROFILE;

import com.auth0.AuthenticationController;
import io.camunda.operate.webapp.security.BaseWebConfigurer;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Autowired
  protected OAuth2WebConfigurer oAuth2WebConfigurer;

  @Bean
  public AuthenticationController authenticationController() {
    return AuthenticationController.newBuilder(operateProperties.getAuth0().getDomain(),
        operateProperties.getAuth0().getClientId(), operateProperties.getAuth0().getClientSecret())
        .build();
  }

  @Override
  protected void configure(final HttpSecurity http) throws Exception {
    configureSecurityHeaders(http);
    http.csrf().disable()
      .authorizeRequests()
      .antMatchers(AUTH_WHITELIST).permitAll()
      .antMatchers(API, PUBLIC_API, ROOT + "**").authenticated()
      .and().exceptionHandling()
        .authenticationEntryPoint(this::failureHandler);
    configureOAuth2(http);
  }

  @Override
  protected void configureOAuth2(HttpSecurity http) throws Exception {
    oAuth2WebConfigurer.configure(http);
  }

}
