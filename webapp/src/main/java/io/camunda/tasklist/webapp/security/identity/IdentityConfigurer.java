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
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Profile(
    TasklistProfileService.IDENTITY_AUTH_PROFILE + " || " + TasklistProfileService.SSO_AUTH_PROFILE)
@Configuration
public class IdentityConfigurer {

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private Environment environment;

  @Bean
  public Identity getIdentity() {
    final Set<String> activeProfiles = Set.of(environment.getActiveProfiles());

    final IdentityConfiguration ic;

    if (activeProfiles.contains(TasklistProfileService.IDENTITY_AUTH_PROFILE)) {
      ic =
          new IdentityConfiguration(
              tasklistProperties.getIdentity().getBaseUrl(),
              tasklistProperties.getIdentity().getIssuerUrl(),
              tasklistProperties.getIdentity().getIssuerBackendUrl(),
              tasklistProperties.getIdentity().getClientId(),
              tasklistProperties.getIdentity().getClientSecret(),
              tasklistProperties.getIdentity().getAudience(),
              IdentityConfiguration.Type.KEYCLOAK.toString());
    } else {
      ic =
          new IdentityConfiguration.Builder()
              .withBaseUrl(tasklistProperties.getIdentity().getBaseUrl())
              .withType(IdentityConfiguration.Type.AUTH0.toString())
              .build();
    }

    return new Identity(ic);
  }
}
