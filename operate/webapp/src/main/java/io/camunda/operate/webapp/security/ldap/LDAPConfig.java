/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.ldap;

import static io.camunda.operate.OperateProfileService.LDAP_AUTH_PROFILE;

import io.camunda.config.operate.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

@Configuration
@Profile(LDAP_AUTH_PROFILE)
public class LDAPConfig {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired private OperateProperties operateProperties;

  @Bean
  public LdapTemplate ldapTemplate() {
    try {
      getContextSource()
          .getContext(
              operateProperties.getLdap().getManagerDn(),
              operateProperties.getLdap().getManagerPassword());
    } catch (Exception e) {
      logger.error("Authentication for lookup failed.", e);
    }
    return new LdapTemplate(getContextSource());
  }

  @Bean
  public LdapContextSource getContextSource() {
    final LdapContextSource contextSource = new LdapContextSource();
    contextSource.setUrl(operateProperties.getLdap().getUrl());
    contextSource.setUserDn(operateProperties.getLdap().getManagerDn());
    contextSource.setPassword(operateProperties.getLdap().getManagerPassword());
    return contextSource;
  }
}
