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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class IdentityConfigurer {

  @Bean
  @Profile(OperateProfileService.IDENTITY_AUTH_PROFILE)
  public Identity getIdentity(OperateProperties operateProperties) {
    return new Identity(new IdentityConfiguration.Builder().withBaseUrl(operateProperties.getIdentity().getBaseUrl())
        .withIssuer(operateProperties.getIdentity().getIssuerUrl())
        .withIssuerBackendUrl(operateProperties.getIdentity().getIssuerBackendUrl())
        .withClientId(operateProperties.getIdentity().getClientId())
        .withClientSecret(operateProperties.getIdentity().getClientSecret())
        .withAudience(operateProperties.getIdentity().getAudience()).build());
  }

  @Bean(name = "saasIdentity")
  @Profile(OperateProfileService.SSO_AUTH_PROFILE)
  @ConditionalOnProperty(prefix = OperateProperties.PREFIX, name = "identity.resourcePermissionsEnabled", havingValue = "true")
  public Identity getSaaSIdentity(OperateProperties operateProperties) {
    return new Identity(new IdentityConfiguration.Builder()
        .withBaseUrl(operateProperties.getIdentity().getBaseUrl())
        .withType(IdentityConfiguration.Type.AUTH0.name()).build());
  }

  @Bean
  @ConditionalOnBean(Identity.class)
  public PermissionsService getPermissionsService(OperateProperties operateProperties) {
    return new PermissionsService(operateProperties);
  }

}
