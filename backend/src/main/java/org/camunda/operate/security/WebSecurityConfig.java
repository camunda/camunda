/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.security;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.io.IOException;
import java.io.PrintWriter;

import javax.json.Json;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.user.ElasticSearchUserDetailsService;
import org.camunda.operate.user.UserStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

@Profile("auth")
@EnableWebSecurity
@Configuration
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

  private static final String RESPONSE_CHARACTER_ENCODING = "UTF-8";
  public static final String X_CSRF_PARAM = "X-CSRF-PARAM";
  public static final String X_CSRF_HEADER = "X-CSRF-HEADER";
  public static final String X_CSRF_TOKEN = "X-CSRF-TOKEN";
  public static final String COOKIE_JSESSIONID = "JSESSIONID";
  public static final String LOGIN_RESOURCE = "/api/login";
  public static final String LOGOUT_RESOURCE = "/api/logout";
  public static final String ACTUATOR_ENDPOINTS = "/actuator/**";
  
  // Used to store the CSRF Token in a cookie.
  private final CookieCsrfTokenRepository cookieCSRFTokenRepository = new CookieCsrfTokenRepository();
  
  @Autowired
  private OperateProperties operateProperties;
  
  @Autowired
  private UserStorage userStorage;
  
  private static final String[] AUTH_WHITELIST = {
    // -- swagger ui
    "/swagger-resources",
    "/swagger-resources/**",
    "/swagger-ui.html",
    "/documentation",
    "/webjars/**",
    "/actuator/prometheus" // TODO: OPE-637
  };  

  @Bean
  public UserDetailsService userDetailsService() {
    return new ElasticSearchUserDetailsService(operateProperties, userStorage,passwordEncoder());
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
  
  @Override
  public void configure(HttpSecurity http) throws Exception {
    if(operateProperties.isCsrfPreventionEnabled()){
      cookieCSRFTokenRepository.setCookieName(X_CSRF_TOKEN);
      http.csrf()
      .ignoringAntMatchers(LOGIN_RESOURCE)
      .and()
      .addFilterAfter(getCSRFHeaderFilter(),CsrfFilter.class);
    }else {
      http.csrf().disable();
    }
    http
      .authorizeRequests()
        .antMatchers(AUTH_WHITELIST).permitAll()
        .antMatchers("/api/**").authenticated()
        .antMatchers(ACTUATOR_ENDPOINTS).hasAuthority("ROLE_ACTRADMIN")
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
        .deleteCookies(COOKIE_JSESSIONID,X_CSRF_TOKEN)
      .and()
      .exceptionHandling().authenticationEntryPoint(this::failureHandler);
  }

  private void logoutSuccessHandler(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    response.setStatus(NO_CONTENT.value());
  }

  private void failureHandler(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
    request.getSession().invalidate();
    response.reset();
    response.setCharacterEncoding(RESPONSE_CHARACTER_ENCODING);
    
    PrintWriter writer = response.getWriter();
    String jsonResponse = Json.createObjectBuilder()
      .add("message", ex.getMessage())
      .build()
      .toString();

    writer.append(jsonResponse);
    
    response.setStatus(UNAUTHORIZED.value());
    response.setContentType(APPLICATION_JSON.getMimeType());
  }
  
  /**
   * Stores the CSRF Token in HTTP Response Header (for REST Clients) and as Cookie (for JavaScript Browser applications)
   * The CSRF Token is expected to be set in HTTP Request Header from the client. 
   * So an attacker can't trick the user to submit unindented data to the server (by a link).
   * @param request
   * @param response
   * @return
   */
  private HttpServletResponse addCSRFTokenWhenAvailable(HttpServletRequest request, HttpServletResponse response) {
    if(shouldAddCSRF(request)) {
      CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
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
    String path = request.getRequestURI();
    if(auth!=null && auth.isAuthenticated() && (path==null || !path.contains("logout"))) {
      return true;
    }
    return false;
  }

  private void successHandler(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    addCSRFTokenWhenAvailable(request, response).setStatus(NO_CONTENT.value());
  }
  
  protected OncePerRequestFilter getCSRFHeaderFilter() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
          filterChain.doFilter(request, addCSRFTokenWhenAvailable(request, response));
      }
    };
  }

}