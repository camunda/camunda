/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.authentication.config.AuthenticationProperties.METHOD;

import io.camunda.security.entity.AuthenticationMethod;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class TasklistProfileServiceImpl implements TasklistProfileService {

  private static final Set<String> CANT_LOGOUT_AUTH_PROFILES = Set.of(SSO_AUTH_PROFILE);

  @Autowired private Environment environment;

  @Override
  public String getMessageByProfileFor(final Exception exception) {
    if (isDevelopmentProfileActive()) {
      return exception.getMessage();
    }
    return "";
  }

  @Override
  public boolean currentProfileCanLogout() {
    return Arrays.stream(environment.getActiveProfiles())
        .noneMatch(CANT_LOGOUT_AUTH_PROFILES::contains);
  }

  @Override
  public boolean isLoginDelegated() {
    return isSSOProfile() || isConsolidatedAuthOidc();
  }

  private boolean isDevelopmentProfileActive() {
    return List.of(environment.getActiveProfiles()).contains("dev");
  }

  private boolean isSSOProfile() {
    return Arrays.asList(environment.getActiveProfiles()).contains(SSO_AUTH_PROFILE);
  }

  public boolean isConsolidatedAuthOidc() {
    final var consolidatedAuthVariation =
        AuthenticationMethod.parse(environment.getProperty(METHOD));

    return consolidatedAuthVariation.isPresent()
        && AuthenticationMethod.OIDC.equals(consolidatedAuthVariation.get());
  }
}
