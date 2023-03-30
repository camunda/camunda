/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile(TasklistProfileService.IDENTITY_AUTH_PROFILE)
@Configuration
public class IdentityConfigurer {

  @Autowired private TasklistProperties tasklistProperties;

  @Bean
  public Identity getIdentity() {
    final IdentityConfiguration ic =
        new IdentityConfiguration(
            tasklistProperties.getIdentity().getBaseUrl(),
            tasklistProperties.getIdentity().getIssuerUrl(),
            tasklistProperties.getIdentity().getIssuerBackendUrl(),
            tasklistProperties.getIdentity().getClientId(),
            tasklistProperties.getIdentity().getClientSecret(),
            tasklistProperties.getIdentity().getAudience(),
            IdentityConfiguration.Type.KEYCLOAK.toString());
    return new Identity(ic);
  }
}
