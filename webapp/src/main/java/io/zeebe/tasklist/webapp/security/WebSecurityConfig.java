/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security;

import static io.zeebe.tasklist.webapp.security.TasklistURIs.AUTH_WHITELIST;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.COOKIE_JSESSIONID;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.ERROR_URL;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.GRAPHQL_URL;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.RESPONSE_CHARACTER_ENCODING;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.X_CSRF_HEADER;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.X_CSRF_PARAM;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.X_CSRF_TOKEN;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import java.io.IOException;
import java.io.PrintWriter;
import javax.json.Json;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Profile(TasklistURIs.AUTH_PROFILE)
@EnableWebSecurity
@Configuration
@Component("webSecurityConfig")
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

  // Used to store the CSRF Token in a cookie.
  private final CookieCsrfTokenRepository cookieCSRFTokenRepository =
      new CookieCsrfTokenRepository();
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
      cookieCSRFTokenRepository.setCookieName(X_CSRF_TOKEN);
      http.csrf()
          .ignoringAntMatchers(LOGIN_RESOURCE)
          .ignoringRequestMatchers(EndpointRequest.to(LoggersEndpoint.class))
          .and()
          .addFilterAfter(getCSRFHeaderFilter(), CsrfFilter.class);
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

  /**
   * Stores the CSRF Token in HTTP Response Header (for REST Clients) and as Cookie (for JavaScript
   * Browser applications) The CSRF Token is expected to be set in HTTP Request Header from the
   * client. So an attacker can't trick the user to submit unindented data to the server (by a
   * link).
   */
  private HttpServletResponse addCSRFTokenWhenAvailable(
      HttpServletRequest request, HttpServletResponse response) {
    if (shouldAddCSRF(request)) {
      final CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
      if (token != null) {
        response.setHeader(X_CSRF_HEADER, token.getHeaderName());
        response.setHeader(X_CSRF_PARAM, token.getParameterName());
        response.setHeader(X_CSRF_TOKEN, token.getToken());
        // We need to access the CSRF Token Cookie from JavaScript too:
        cookieCSRFTokenRepository.setCookieHttpOnly(false);
        cookieCSRFTokenRepository.saveToken(token, request, response);
      }
    }
    return response;
  }

  protected boolean shouldAddCSRF(HttpServletRequest request) {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    final String path = request.getRequestURI();
    if (auth != null && auth.isAuthenticated() && (path == null || !path.contains("logout"))) {
      return true;
    }
    return false;
  }

  private void successHandler(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    addCSRFTokenWhenAvailable(request, response).setStatus(NO_CONTENT.value());
  }

  protected OncePerRequestFilter getCSRFHeaderFilter() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
          HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
          throws ServletException, IOException {
        filterChain.doFilter(request, addCSRFTokenWhenAvailable(request, response));
      }
    };
  }
}
