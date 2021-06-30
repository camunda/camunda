/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.ldap;

import static io.camunda.operate.webapp.security.OperateURIs.AUTH_WHITELIST;
import static io.camunda.operate.webapp.security.OperateURIs.COOKIE_JSESSIONID;
import static io.camunda.operate.webapp.security.OperateURIs.LDAP_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.X_CSRF_TOKEN;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import io.camunda.operate.property.LdapProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.CSRFProtectable;
import java.io.IOException;
import java.io.PrintWriter;
import javax.json.Json;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Profile(LDAP_AUTH_PROFILE)
@Configuration
@EnableWebSecurity
@Component("webSecurityConfig")
public class LDAPWebSecurityConfig extends WebSecurityConfigurerAdapter implements CSRFProtectable {

  private static final String RESPONSE_CHARACTER_ENCODING = "UTF-8";

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public void configure(AuthenticationManagerBuilder auth) throws Exception {
    LdapProperties ldapConfig = operateProperties.getLdap();
    if (StringUtils.hasText(ldapConfig.getDomain())) {
      setUpActiveDirectoryLDAP(auth, ldapConfig);
    } else {
      setupStandardLDAP(auth, ldapConfig);
    }
  }

  @Override
  public void configure(HttpSecurity http) throws Exception {
    if (operateProperties.isCsrfPreventionEnabled()) {
      configureCSRF(http);
    } else {
      http.csrf().disable();
    }
    http
        .authorizeRequests()
        .antMatchers(AUTH_WHITELIST).permitAll()
        .antMatchers("/api/**").authenticated()
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
        .exceptionHandling().authenticationEntryPoint(this::failureHandler);
  }

  private void setUpActiveDirectoryLDAP(AuthenticationManagerBuilder auth,
      LdapProperties ldapConfig) {
    ActiveDirectoryLdapAuthenticationProvider adLDAPProvider =
        new ActiveDirectoryLdapAuthenticationProvider(
            ldapConfig.getDomain(),
            ldapConfig.getUrl(),
            ldapConfig.getBaseDn());
    if (StringUtils.hasText(ldapConfig.getUserSearchFilter())) {
      adLDAPProvider.setSearchFilter(ldapConfig.getUserSearchFilter());
    }
    adLDAPProvider.setConvertSubErrorCodesToExceptions(true);
    auth.authenticationProvider(adLDAPProvider);
  }

  private void setupStandardLDAP(AuthenticationManagerBuilder auth, LdapProperties ldapConfig)
      throws Exception {
    auth.ldapAuthentication()
        .userDnPatterns(ldapConfig.getUserDnPatterns())
        .userSearchFilter(ldapConfig.getUserSearchFilter())
        .userSearchBase(ldapConfig.getUserSearchBase())
        .contextSource()
        .url(ldapConfig.getUrl() + ldapConfig.getBaseDn())
        .managerDn(ldapConfig.getManagerDn())
        .managerPassword(ldapConfig.getManagerPassword());
  }

  private void logoutSuccessHandler(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) {
    response.setStatus(NO_CONTENT.value());
  }

  private void successHandler(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) {
    addCSRFTokenWhenAvailable(request, response).setStatus(NO_CONTENT.value());
  }

  private void failureHandler(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException ex) throws IOException {
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

  @Bean
  public LdapContextSource contextSource() {
    LdapContextSource contextSource = new LdapContextSource();
    contextSource.setUrl(operateProperties.getLdap().getUrl());
    contextSource.setUserDn(operateProperties.getLdap().getManagerDn());
    contextSource.setPassword(operateProperties.getLdap().getManagerPassword());
    return contextSource;
  }

  @Bean
  public LdapTemplate ldapTemplate() {
    return new LdapTemplate(contextSource());
  }
}
