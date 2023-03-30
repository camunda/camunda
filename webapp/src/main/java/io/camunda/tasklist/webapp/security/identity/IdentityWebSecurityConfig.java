/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.identity;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ALL_REST_V1_API;
import static io.camunda.tasklist.webapp.security.TasklistURIs.AUTH_WHITELIST;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ERROR_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.GRAPHQL_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.REQUESTED_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.REST_V1_API;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ROOT_URL;
import static org.apache.commons.lang3.StringUtils.containsAny;

import io.camunda.tasklist.webapp.security.BaseWebConfigurer;
import io.camunda.tasklist.webapp.security.oauth.IdentityOAuth2WebConfigurer;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Profile(IDENTITY_AUTH_PROFILE)
@EnableWebSecurity
@Component("webSecurityConfig")
public class IdentityWebSecurityConfig extends BaseWebConfigurer {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired protected IdentityOAuth2WebConfigurer oAuth2WebConfigurer;

  @Override
  protected void configureOAuth2(HttpSecurity http) throws Exception {
    oAuth2WebConfigurer.configure(http);
  }

  @Override
  protected void configureCsrf(HttpSecurity http) throws Exception {
    http.csrf()
        .disable()
        .authorizeRequests()
        .antMatchers(AUTH_WHITELIST)
        .permitAll()
        .antMatchers(GRAPHQL_URL, ALL_REST_V1_API, ROOT_URL, ERROR_URL)
        .authenticated()
        .and()
        .exceptionHandling()
        .authenticationEntryPoint(this::authenticationEntry);
  }

  protected void authenticationEntry(
      HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
      throws IOException {
    String requestedUrl = req.getRequestURI();
    if (req.getQueryString() != null && !req.getQueryString().isEmpty()) {
      requestedUrl = requestedUrl + "?" + req.getQueryString();
    }

    if (containsAny(requestedUrl.toLowerCase(), GRAPHQL_URL, REST_V1_API)) {
      sendJSONErrorMessage(res, ex.getMessage());
    } else {
      logger.debug("Try to access protected resource {}. Save it for later redirect", requestedUrl);
      req.getSession().setAttribute(REQUESTED_URL, requestedUrl);
      res.sendRedirect(req.getContextPath() + LOGIN_RESOURCE);
    }
  }
}
