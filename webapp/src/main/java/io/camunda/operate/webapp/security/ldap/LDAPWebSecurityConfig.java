/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.ldap;

import static io.camunda.operate.webapp.security.OperateProfileService.LDAP_AUTH_PROFILE;
import static org.springframework.http.HttpStatus.NO_CONTENT;

import io.camunda.operate.property.LdapProperties;
import io.camunda.operate.webapp.security.BaseWebConfigurer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
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

  @Autowired
  private LdapContextSource contextSource;

  @Autowired
  private LDAPUserService userService;

  @Override
  public void configure(AuthenticationManagerBuilder auth) throws Exception {
    LdapProperties ldapConfig = operateProperties.getLdap();
    if (StringUtils.hasText(ldapConfig.getDomain())) {
      setUpActiveDirectoryLDAP(auth, ldapConfig);
    } else {
      setupStandardLDAP(auth, ldapConfig);
    }
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

  @Bean
  public LdapContextSource contextSource() {
    LdapContextSource contextSource = new LdapContextSource();
    contextSource.setUrl(operateProperties.getLdap().getUrl());
    contextSource.setUserDn(operateProperties.getLdap().getManagerDn());
    contextSource.setPassword(operateProperties.getLdap().getManagerPassword());
    return contextSource;
  }

  private void authenticateContextSource(final LdapContextSource contextSource) {
    try {
      contextSource
          .getContext(operateProperties.getLdap().getManagerDn(),
              operateProperties.getLdap().getManagerPassword());
    }catch (Exception e){
      logger.error("Authentication for lookup failed.", e);
    }
  }

  @Bean
  public LdapTemplate ldapTemplate() {
    authenticateContextSource(contextSource);
    return new LdapTemplate(contextSource);
  }

  @Override
  protected void logoutSuccessHandler(final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {
    userService.cleanUp(authentication);
    super.logoutSuccessHandler(request, response, authentication);
  }
}
