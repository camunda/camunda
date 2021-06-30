/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.iam;

import static io.camunda.operate.webapp.security.OperateURIs.API;
import static io.camunda.operate.webapp.security.OperateURIs.AUTH_WHITELIST;
import static io.camunda.operate.webapp.security.OperateURIs.IAM_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;

import io.camunda.iam.sdk.IamApi;
import io.camunda.iam.sdk.IamApiConfiguration;
import io.camunda.operate.property.IamProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.CSRFProtectable;
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

  public static final String REQUESTED_URL = "requestedUrl";
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());
  @Autowired
  private OperateProperties operateProperties;

  @Bean
  public IamApi iamApi() throws IllegalArgumentException {
    final IamProperties props = operateProperties.getIam();
    final IamApiConfiguration configuration =
        new IamApiConfiguration(props.getIssuerUrl(), props.getClientId(), props.getClientSecret());
    return new IamApi(configuration);
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    if (operateProperties.isCsrfPreventionEnabled()) {
      configureCSRF(http);
    } else {
      http.csrf().disable();
    }
    http
        .authorizeRequests()
        .antMatchers(AUTH_WHITELIST).permitAll()
        .antMatchers(API, ROOT).authenticated()
        .and().exceptionHandling()
        .authenticationEntryPoint(this::authenticationEntry);
  }

  protected void authenticationEntry(HttpServletRequest req, HttpServletResponse res,
      AuthenticationException ex) throws IOException {
    String requestedUrl = req.getRequestURI();
    if (req.getQueryString() != null && !req.getQueryString().isEmpty()) {
      requestedUrl = requestedUrl + "?" + req.getQueryString();
    }
    logger.debug("Try to access protected resource {}. Save it for later redirect", requestedUrl);
    req.getSession().setAttribute(REQUESTED_URL, requestedUrl);
    res.sendRedirect(req.getContextPath() + LOGIN_RESOURCE);
  }

}
