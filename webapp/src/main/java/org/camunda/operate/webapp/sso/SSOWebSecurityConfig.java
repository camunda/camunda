package org.camunda.operate.webapp.sso;
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */


import java.io.UnsupportedEncodingException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

import com.auth0.AuthenticationController;

@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
@Configuration
@EnableWebSecurity
public class SSOWebSecurityConfig extends WebSecurityConfigurerAdapter {
  
  public static final String SSO_AUTH_PROFILE = "sso-auth";
  public static final String LOGIN_RESOURCE = "/api/login";
  public static final String CALLBACK_URI = "/sso-callback";
  public static final String ACTUATOR_ENDPOINTS = "/actuator/**";
  
  private static final String[] AUTH_WHITELIST = {
      // -- swagger ui
      "/swagger-resources",
      "/swagger-resources/**",
      "/swagger-ui.html",
      "/documentation",
      "/webjars/**",
      "/error",
      ACTUATOR_ENDPOINTS,
      LOGIN_RESOURCE
    };  
  /**
   * This is your auth0 domain (tenant you have created when registering with auth0 - account name)
   */
  @Value(value = "${camunda.operate.auth0.domain:camunda-dev.eu.auth0.com}")
  private String domain; 

  /**
   * This is the client id of your auth0 application (see Settings page on auth0 dashboard)
   */
  @Value(value = "${camunda.operate.auth0.clientId}")
  private String clientId;

  /**
   * This is the client secret of your auth0 application (see Settings page on auth0 dashboard)
   */
  @Value(value = "${camunda.operate.auth0.clientSecret}")
  private String clientSecret;
  
  @Value(value = "${camunda.operate.auth0.claimName:https://camunda.com/orgs}") 
  private String claimName; 
  
  @Value(value = "${camunda.operate.auth0.organization}")
  private String organization;
  
  /**
   * Key for claim to retrieve the username 
   */
  private String nameKey = "name";

//  @Bean
//  public LogoutSuccessHandler logoutSuccessHandler() {
//      return new LogoutHandler();
//  }

  @Bean
  public AuthenticationController authenticationController() throws UnsupportedEncodingException {
      return AuthenticationController.newBuilder(domain, clientId, clientSecret)
              .build();
  }
 
  @Override
  protected void configure(HttpSecurity http) throws Exception {
      http.csrf().disable();

      http
      .authorizeRequests()
        .antMatchers(AUTH_WHITELIST).permitAll()
        .antMatchers("/api/**","/").authenticated();
      // for now we don't want logout for SSO
      //.and()
      // .logout().logoutSuccessHandler(logoutSuccessHandler()).permitAll();
  }

  public String getDomain() {
      return domain;
  }

  public String getClientId() {
      return clientId;
  }

  public String getClientSecret() {
      return clientSecret;
  }

  public String getClaimName() {
    return claimName;
  }

  public String getOrganization() {
    return organization;
  }
  
  public String getNameKey() {
    return nameKey;
  }
}
