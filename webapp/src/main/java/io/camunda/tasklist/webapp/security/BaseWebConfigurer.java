/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.security.TasklistURIs.ALL_REST_V1_API;
import static io.camunda.tasklist.webapp.security.TasklistURIs.AUTH_WHITELIST;
import static io.camunda.tasklist.webapp.security.TasklistURIs.COOKIE_JSESSIONID;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ERROR_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.GRAPHQL_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.RESPONSE_CHARACTER_ENCODING;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import io.camunda.tasklist.property.TasklistProperties;
import java.io.IOException;
import java.io.PrintWriter;
import javax.json.Json;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

public abstract class BaseWebConfigurer extends WebSecurityConfigurerAdapter {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired TasklistProfileService errorMessageService;

  @Autowired private TasklistProfileService profileService;

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    configureContentPolicySecurityHeader(http);
    configureCsrf(http);
    configureOAuth2(http);
  }

  protected abstract void configureOAuth2(HttpSecurity http) throws Exception;

  protected void configureContentPolicySecurityHeader(HttpSecurity http) throws Exception {
    http.headers()
        .contentSecurityPolicy(
            tasklistProperties.getSecurityProperties().getContentSecurityPolicy());
  }

  protected void configureCsrf(HttpSecurity http) throws Exception {
    http.csrf()
        .disable()
        .authorizeRequests()
        .antMatchers(AUTH_WHITELIST)
        .permitAll()
        .antMatchers(GRAPHQL_URL, ALL_REST_V1_API, ERROR_URL)
        .authenticated()
        .and()
        .formLogin()
        .loginProcessingUrl(LOGIN_RESOURCE)
        .successHandler(this::successHandler)
        .failureHandler(this::failureHandler)
        .permitAll()
        .and()
        .logout()
        .logoutUrl(LOGOUT_RESOURCE)
        .logoutSuccessHandler(this::logoutSuccessHandler)
        .permitAll()
        .invalidateHttpSession(true)
        .deleteCookies(COOKIE_JSESSIONID)
        .and()
        .exceptionHandling()
        .authenticationEntryPoint(this::failureHandler);
  }

  private void logoutSuccessHandler(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    response.setStatus(NO_CONTENT.value());
  }

  private void failureHandler(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    request.getSession().invalidate();
    sendJSONErrorMessage(response, profileService.getMessageByProfileFor(ex));
  }

  public static void sendJSONErrorMessage(final HttpServletResponse response, final String message)
      throws IOException {
    response.reset();
    response.setCharacterEncoding(RESPONSE_CHARACTER_ENCODING);

    final PrintWriter writer = response.getWriter();
    final String jsonResponse =
        Json.createObjectBuilder().add("message", message).build().toString();

    writer.append(jsonResponse);

    response.setStatus(UNAUTHORIZED.value());
    response.setContentType(APPLICATION_JSON.getMimeType());
  }

  private void successHandler(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    response.setStatus(NO_CONTENT.value());
  }
}
