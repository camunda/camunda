/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security.sso;

import static io.zeebe.tasklist.webapp.security.TasklistURIs.AUTH_WHITELIST;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.ERROR_URL;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.GRAPHQL_URL;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.ROOT_URL;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.SSO_AUTH_PROFILE;

import com.auth0.AuthenticationController;
import io.zeebe.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Profile(SSO_AUTH_PROFILE)
@Configuration
@EnableWebSecurity
@Component("webSecurityConfig")
public class SSOWebSecurityConfig extends WebSecurityConfigurerAdapter {

  public static final String REQUESTED_URL = "requestedUrl";
  private static final Logger LOGGER = LoggerFactory.getLogger(SSOController.class);
  /**
   * Defines the domain which the user always sees<br>
   * auth0.com call it <b>Custom Domain</b>
   */
  @Value(value = "${zeebe.tasklist.auth0.domain}")
  private String domain;

  /**
   * Defines the domain which provides information about the user<br>
   * auth0.com call it <b>Domain</b>
   */
  @Value(value = "${zeebe.tasklist.auth0.backendDomain}")
  private String backendDomain;

  /**
   * This is the client id of auth0 application (see Settings page on auth0 dashboard) It's like an
   * user name for the application
   */
  @Value(value = "${zeebe.tasklist.auth0.clientId}")
  private String clientId;

  /**
   * This is the client secret of auth0 application (see Settings page on auth0 dashboard) It's like
   * a password for the application
   */
  @Value(value = "${zeebe.tasklist.auth0.clientSecret}")
  private String clientSecret;

  /** The claim we want to check It's like a permission name */
  @Value(value = "${zeebe.tasklist.auth0.claimName}")
  private String claimName;

  /** The given organization should be contained in value of claim key (claimName) - MUST given */
  @Value(value = "${zeebe.tasklist.auth0.organization}")
  private String organization;

  /** Key for claim to retrieve the user name */
  private String nameKey = "name";

  @Autowired private OAuth2WebConfigurer oAuth2WebConfigurer;

  @Bean
  public AuthenticationController authenticationController() throws UnsupportedEncodingException {
    return AuthenticationController.newBuilder(domain, clientId, clientSecret).build();
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.csrf()
        .disable()
        .authorizeRequests()
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
    String requestedUrl = req.getRequestURI().toString();
    if (req.getQueryString() != null && !req.getQueryString().isEmpty()) {
      requestedUrl = requestedUrl + "?" + req.getQueryString();
    }
    LOGGER.debug("Try to access protected resource {}. Save it for later redirect", requestedUrl);
    req.getSession().setAttribute(REQUESTED_URL, requestedUrl);
    res.sendRedirect(req.getContextPath() + LOGIN_RESOURCE);
  }

  /** Called <b>Custom domain</b> at auth0.com */
  public String getDomain() {
    return domain;
  }

  /** Called <b>Domain</b> at auth0.com */
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
