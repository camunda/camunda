/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.iam;

import static io.camunda.tasklist.webapp.security.TasklistURIs.AUTH_WHITELIST;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ERROR_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.GRAPHQL_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.IAM_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.REQUESTED_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ROOT_URL;

import io.camunda.iam.sdk.IamApi;
import io.camunda.iam.sdk.IamApiConfiguration;
import io.camunda.tasklist.property.IamProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.CSRFProtectable;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Profile(IAM_AUTH_PROFILE)
@Configuration
@EnableWebSecurity
@Component("webSecurityConfig")
public class IAMWebSecurityConfig extends WebSecurityConfigurerAdapter implements CSRFProtectable {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired private OAuth2WebConfigurer oAuth2WebConfigurer;
  @Autowired private TasklistProperties tasklistProperties;

  @Bean
  public IamApi iamApi() throws IllegalArgumentException {
    final IamProperties props = tasklistProperties.getIam();
    final IamApiConfiguration configuration =
        new IamApiConfiguration(props.getIssuerUrl(), props.getClientId(), props.getClientSecret());
    return new IamApi(configuration);
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    if (tasklistProperties.isCsrfPreventionEnabled()) {
      configureCSRF(http);
    } else {
      http.csrf().disable();
    }
    http.authorizeRequests()
        .antMatchers(AUTH_WHITELIST)
        .permitAll()
        .antMatchers(GRAPHQL_URL, ROOT_URL, ERROR_URL)
        .authenticated()
        .and()
        .exceptionHandling()
        .authenticationEntryPoint(this::authenticationEntry);
    oAuth2WebConfigurer.configure(http);
  }

  protected void authenticationEntry(
      HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
      throws IOException {
    String requestedUrl = req.getRequestURI();
    if (req.getQueryString() != null && !req.getQueryString().isEmpty()) {
      requestedUrl = requestedUrl + "?" + req.getQueryString();
    }
    logger.debug("Try to access protected resource {}. Save it for later redirect", requestedUrl);
    req.getSession().setAttribute(REQUESTED_URL, requestedUrl);
    res.sendRedirect(req.getContextPath() + LOGIN_RESOURCE);
  }
}
