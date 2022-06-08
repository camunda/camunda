/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.OperateProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile(OperateProfileService.IDENTITY_AUTH_PROFILE)
@Configuration
public class IdentityConfigurer {

  @Autowired
  private OperateProperties operateProperties;

  @Bean
  public Identity getIdentity() {
    return new Identity(new IdentityConfiguration(
        operateProperties.getIdentity().getIssuerUrl(),
        operateProperties.getIdentity().getIssuerBackendUrl(),
        operateProperties.getIdentity().getClientId(),
        operateProperties.getIdentity().getClientSecret(),
        operateProperties.getIdentity().getAudience()));
  }

}
