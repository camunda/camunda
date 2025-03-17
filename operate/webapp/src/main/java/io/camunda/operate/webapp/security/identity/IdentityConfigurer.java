/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.operate.OperateProfileService;
import io.camunda.config.operate.OperateProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class IdentityConfigurer {

  @Bean(name = "saasIdentity")
  @Profile(OperateProfileService.SSO_AUTH_PROFILE)
  @ConditionalOnProperty(
      prefix = OperateProperties.PREFIX,
      name = "identity.resourcePermissionsEnabled",
      havingValue = "true")
  public Identity getSaaSIdentity(final OperateProperties operateProperties) {
    return new Identity(
        new IdentityConfiguration.Builder()
            .withBaseUrl(operateProperties.getIdentity().getBaseUrl())
            .withType(IdentityConfiguration.Type.AUTH0.name())
            .build());
  }
}
