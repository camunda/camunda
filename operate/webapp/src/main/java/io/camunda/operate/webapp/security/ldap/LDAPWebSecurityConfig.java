/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.ldap;

import static io.camunda.operate.OperateProfileService.LDAP_AUTH_PROFILE;

import io.camunda.operate.OperateProfileService;
import io.camunda.config.operate.LdapProperties;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.webapp.security.BaseWebConfigurer;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Profile(LDAP_AUTH_PROFILE)
@Configuration
@EnableWebSecurity
@Component("webSecurityConfig")
public class LDAPWebSecurityConfig extends BaseWebConfigurer {
  protected OAuth2WebConfigurer oAuth2WebConfigurer;
  private final LDAPUserService userService;

  public LDAPWebSecurityConfig(
      final OperateProperties operateProperties,
      final OperateProfileService errorMessageService,
      final OAuth2WebConfigurer oAuth2WebConfigurer,
      final LDAPUserService ldapUserService) {
    super(operateProperties, errorMessageService);
    this.oAuth2WebConfigurer = oAuth2WebConfigurer;
    userService = ldapUserService;
  }

  @Override
  protected void applyAuthenticationSettings(final AuthenticationManagerBuilder auth)
      throws Exception {
    final LdapProperties ldapConfig = operateProperties.getLdap();
    if (StringUtils.hasText(ldapConfig.getDomain())) {
      setUpActiveDirectoryLDAP(auth, ldapConfig);
    } else {
      setupStandardLDAP(auth, ldapConfig);
    }
  }

  @Override
  protected void applyOAuth2Settings(final HttpSecurity http) throws Exception {
    oAuth2WebConfigurer.configure(http);
  }

  @Override
  protected void logoutSuccessHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {
    userService.cleanUp(authentication);
    super.logoutSuccessHandler(request, response, authentication);
  }

  private void setUpActiveDirectoryLDAP(
      final AuthenticationManagerBuilder auth, final LdapProperties ldapConfig) {
    final ActiveDirectoryLdapAuthenticationProvider adLDAPProvider =
        new ActiveDirectoryLdapAuthenticationProvider(
            ldapConfig.getDomain(), ldapConfig.getUrl(), ldapConfig.getBaseDn());
    if (StringUtils.hasText(ldapConfig.getUserSearchFilter())) {
      adLDAPProvider.setSearchFilter(ldapConfig.getUserSearchFilter());
    }
    adLDAPProvider.setConvertSubErrorCodesToExceptions(true);
    auth.authenticationProvider(adLDAPProvider);
  }

  private void setupStandardLDAP(
      final AuthenticationManagerBuilder auth, final LdapProperties ldapConfig) throws Exception {
    auth.ldapAuthentication()
        .userDnPatterns(ldapConfig.getUserDnPatterns())
        .userSearchFilter(ldapConfig.getUserSearchFilter())
        .userSearchBase(ldapConfig.getUserSearchBase())
        .contextSource()
        .url(ldapConfig.getUrl() + ldapConfig.getBaseDn())
        .managerDn(ldapConfig.getManagerDn())
        .managerPassword(ldapConfig.getManagerPassword());
  }
}
