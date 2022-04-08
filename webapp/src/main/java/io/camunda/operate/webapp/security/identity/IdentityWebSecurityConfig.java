/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.operate.property.IdentityProperties;
import io.camunda.operate.webapp.security.BaseWebConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.stereotype.Component;

import static io.camunda.operate.webapp.security.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.*;

@Profile(IDENTITY_AUTH_PROFILE)
@Configuration
@EnableWebSecurity
@Component("webSecurityConfig")
public class IdentityWebSecurityConfig extends BaseWebConfigurer {

  @Bean
  public Identity identity() throws IllegalArgumentException {
    final IdentityProperties props = operateProperties.getIdentity();
    final IdentityConfiguration configuration =
        new IdentityConfiguration(props.getIssuerUrl(), props.getIssuerBackendUrl(), props.getClientId(), props.getClientSecret(), props.getAudience());
    return new Identity(configuration);
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.csrf().disable()
        .authorizeRequests()
        .antMatchers(AUTH_WHITELIST).permitAll()
        .antMatchers(API, PUBLIC_API, ROOT).authenticated()
        .and().exceptionHandling()
        .authenticationEntryPoint(this::failureHandler);
    oAuth2WebConfigurer.configure(http);
  }

}
