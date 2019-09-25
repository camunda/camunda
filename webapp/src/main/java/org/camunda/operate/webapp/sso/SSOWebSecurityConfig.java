/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.sso;

import java.io.UnsupportedEncodingException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.stereotype.Component;

import com.auth0.AuthenticationController;

@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
@Configuration
@EnableWebSecurity
@Component("webSecurityConfig")
public class SSOWebSecurityConfig extends WebSecurityConfigurerAdapter {

  public static final String ROOT = "/";
  private static final String API = "/api/**";
  public static final String SSO_AUTH_PROFILE = "sso-auth";
  public static final String LOGIN_RESOURCE = "/api/login";
  public static final String LOGOUT_RESOURCE = "/api/logout";
  public static final String CALLBACK_URI = "/sso-callback";
  public static final String NO_PERMISSION = "/noPermission";

  public static final String ACTUATOR_ENDPOINTS = "/actuator/**";

  private static final String[] AUTH_WHITELIST = {
      // -- swagger ui
      "/swagger-resources",
      "/swagger-resources/**",
      "/swagger-ui.html",
      "/documentation",
      "/webjars/**",
      "/error",
      NO_PERMISSION,
      LOGOUT_RESOURCE,
      ACTUATOR_ENDPOINTS,
      LOGIN_RESOURCE 
   };

  /**
   * Defines the domain which the user always sees<br/>
   * auth0.com call it <b>Custom Domain</b>
   */
  @Value(value = "${camunda.operate.auth0.domain:login.cloud.ultrawombat.com}")
  private String domain;

  /**
   * Defines the domain which provides information about the user<br/>
   * auth0.com call it <b>Domain</b>
   */
  @Value(value = "${camunda.operate.auth0.backendDomain:camunda-dev.eu.auth0.com}")
  private String backendDomain;

  /**
   * This is the client id of auth0 application (see Settings page on auth0
   * dashboard) It's like an user name for the application - MUST given
   */
  @Value(value = "${camunda.operate.auth0.clientId}")
  private String clientId;

  /**
   * This is the client secret of auth0 application (see Settings page on auth0
   * dashboard) It's like a password for the application - MUST given 
   */
  @Value(value = "${camunda.operate.auth0.clientSecret}") 
  private String clientSecret;

  /**
   * The claim we want to check It's like a permission name
   */
  @Value(value = "${camunda.operate.auth0.claimName:https://camunda.com/orgs}")
  private String claimName;

  /**
   * The given organization should be contained in value of claim key 
   * (claimName) - MUST given
   */
  @Value(value = "${camunda.operate.auth0.organization}")
  private String organization;

  /**
   * Key for claim to retrieve the user name
   */
  private String nameKey = "name";

  @Bean
  public AuthenticationController authenticationController() throws UnsupportedEncodingException {
    return AuthenticationController.newBuilder(domain, clientId, clientSecret).build();
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http
      .csrf().disable()
      .authorizeRequests()
      .antMatchers(AUTH_WHITELIST).permitAll()
      .antMatchers(API, ROOT).authenticated()
      .and().exceptionHandling()
        .authenticationEntryPoint((req, res, ex) -> res.sendRedirect(LOGIN_RESOURCE));
  }

  /**
   * Called <b>Custom domain</b> at auth0.com
   * 
   * @return
   */
  public String getDomain() {
    return domain;
  }

  /**
   * Called <b>Domain</b> at auth0.com
   * 
   * @return
   */
  public String getBackendDomain() {
    return backendDomain;
  }

  public String getClientId() {
    return clientId;
  }

  public String getClaimName() {
    return claimName;
  }

  // Expected value in claim values
  public String getOrganization() {
    return organization;
  }

  // Key for getting user name
  public String getNameKey() {
    return nameKey;
  }
}
