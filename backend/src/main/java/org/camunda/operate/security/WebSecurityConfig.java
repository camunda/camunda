/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.security;

import javax.json.Json;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Profile("auth")
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

  public static final String COOKIE_JSESSIONID = "JSESSIONID";
  public static final String LOGIN_RESOURCE = "/api/login";
  public static final String LOGOUT_RESOURCE = "/api/logout";
  public static final String ACTUATOR_ENDPOINTS = "/actuator/**";

  private static final String[] AUTH_WHITELIST = {
    // -- swagger ui
    "/swagger-resources",
    "/swagger-resources/**",
    "/swagger-ui.html",
    "/documentation",
    "/webjars/**"
  };

  @Bean
  public UserDetailsService userDetailsService() {
    String demoPsw = passwordEncoder().encode("demo");
    UserDetails demoUserDetails = User.builder()
      .username("demo")
      .password(demoPsw)
      .roles("USER")
      .build();

    String actadminPsw = passwordEncoder().encode("act");
    UserDetails actadminUserDetails = User.builder()
      .username("act")
      .password(actadminPsw)
      .roles("ACTRADMIN")
      .build();

    InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
    manager.createUser(demoUserDetails);
    manager.createUser(actadminUserDetails);
    return manager;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Override
  public void configure(HttpSecurity http) throws Exception {
    http
      .csrf().disable()
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
        .deleteCookies(COOKIE_JSESSIONID)
      .and()
      .exceptionHandling().authenticationEntryPoint(this::failureHandler);
  }

  private void logoutSuccessHandler(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    response.setStatus(NO_CONTENT.value());
  }

  private void failureHandler(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
    response.setCharacterEncoding("UTF-8");
    PrintWriter writer = response.getWriter();
    String jsonResponse = Json.createObjectBuilder()
      .add("message", ex.getMessage())
      .build()
      .toString();

    writer.append(jsonResponse);
    
    response.setStatus(UNAUTHORIZED.value());
    response.setContentType(APPLICATION_JSON.getMimeType());
  }

  private void successHandler(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Authentication authentication) {
    httpServletResponse.setStatus(NO_CONTENT.value());
  }

}
