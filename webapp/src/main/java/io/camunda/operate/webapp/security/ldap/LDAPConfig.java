/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.ldap;

import io.camunda.operate.property.OperateProperties;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

@Configuration
public class LDAPConfig {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private OperateProperties operateProperties;

  private LdapContextSource contextSource;

  @PostConstruct
  public void init(){
    contextSource = new LdapContextSource();
    contextSource.setUrl(operateProperties.getLdap().getUrl());
    contextSource.setUserDn(operateProperties.getLdap().getManagerDn());
    contextSource.setPassword(operateProperties.getLdap().getManagerPassword());
  }

  @Bean
  public LdapTemplate ldapTemplate() {
    try {
      contextSource
          .getContext(operateProperties.getLdap().getManagerDn(),
              operateProperties.getLdap().getManagerPassword());
    }catch (Exception e){
      logger.error("Authentication for lookup failed.", e);
    }
    return new LdapTemplate(contextSource);
  }

  @Bean
  public LdapContextSource contextSource() {
    return contextSource;
  }
}
