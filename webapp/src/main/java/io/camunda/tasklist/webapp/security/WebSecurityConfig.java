/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.security.TasklistURIs.AUTH_WHITELIST;
import static io.camunda.tasklist.webapp.security.TasklistURIs.COOKIE_JSESSIONID;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ERROR_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.GRAPHQL_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.RESPONSE_CHARACTER_ENCODING;
import static io.camunda.tasklist.webapp.security.TasklistURIs.X_CSRF_TOKEN;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import java.io.IOException;
import java.io.PrintWriter;
import javax.json.Json;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Profile(TasklistURIs.AUTH_PROFILE)
@EnableWebSecurity
@Configuration
@Component("webSecurityConfig")
public class WebSecurityConfig extends WebSecurityConfigurerAdapter implements CSRFProtectable {

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private UserDetailsService userDetailsService;

  @Autowired private OAuth2WebConfigurer oAuth2WebConfigurer;

  @Override
  public void configure(AuthenticationManagerBuilder builder) throws Exception {
    builder.userDetailsService(userDetailsService);
  }

  @Override
  public void configure(HttpSecurity http) throws Exception {
    if (tasklistProperties.isCsrfPreventionEnabled()) {
      configureCSRF(http);
    } else {
      http.csrf().disable();
    }
    http.authorizeRequests()
        .antMatchers(AUTH_WHITELIST)
        .permitAll()
        .antMatchers(GRAPHQL_URL, ERROR_URL)
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
        .deleteCookies(COOKIE_JSESSIONID, X_CSRF_TOKEN)
        .and()
        .exceptionHandling()
        .authenticationEntryPoint(this::failureHandler);
    oAuth2WebConfigurer.configure(http);
  }

  private void logoutSuccessHandler(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    response.setStatus(NO_CONTENT.value());
  }

  private void failureHandler(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    request.getSession().invalidate();
    response.reset();
    response.setCharacterEncoding(RESPONSE_CHARACTER_ENCODING);

    final PrintWriter writer = response.getWriter();
    final String jsonResponse =
        Json.createObjectBuilder().add("message", ex.getMessage()).build().toString();

    writer.append(jsonResponse);

    response.setStatus(UNAUTHORIZED.value());
    response.setContentType(APPLICATION_JSON.getMimeType());
  }

  private void successHandler(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    addCSRFTokenWhenAvailable(request, response).setStatus(NO_CONTENT.value());
  }
}
