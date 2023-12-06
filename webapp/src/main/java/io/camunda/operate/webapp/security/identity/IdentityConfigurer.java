/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.property.OperateProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class IdentityConfigurer {

  @Bean(name = "saasIdentity")
  @Profile(OperateProfileService.SSO_AUTH_PROFILE)
  @ConditionalOnProperty(prefix = OperateProperties.PREFIX, name = "identity.resourcePermissionsEnabled", havingValue = "true")
  public Identity getSaaSIdentity(OperateProperties operateProperties) {
    return new Identity(new IdentityConfiguration.Builder()
        .withBaseUrl(operateProperties.getIdentity().getBaseUrl())
        .withType(IdentityConfiguration.Type.AUTH0.name()).build());
  }

  @Bean
  @ConditionalOnProperty("camunda.identity.baseUrl")
  public PermissionsService getPermissionsService(OperateProperties operateProperties) {
    return new PermissionsService(operateProperties);
  }

}
