/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class OperateProfileService {

  public static final String SSO_AUTH_PROFILE = "sso-auth";
  public static final String IAM_AUTH_PROFILE = "iam-auth";
  public static final String AUTH_PROFILE = "auth";
  public static final String DEFAULT_AUTH = AUTH_PROFILE;
  public static final String LDAP_AUTH_PROFILE = "ldap-auth";
  public static final Set<String> AUTH_PROFILES = Set.of(AUTH_PROFILE,
      LDAP_AUTH_PROFILE,
      SSO_AUTH_PROFILE,
      IAM_AUTH_PROFILE);

  private static final Set<String> CANT_LOGOUT_AUTH_PROFILES =
      Set.of(SSO_AUTH_PROFILE, IAM_AUTH_PROFILE);

  @Autowired
  private Environment environment;

  public String getMessageByProfileFor(final Exception exception) {
    if(exception!=null && isDevelopmentProfileActive()){
      return exception.getMessage();
    }
    return "";
  }

  public boolean isDevelopmentProfileActive() {
    return List.of(environment.getActiveProfiles()).contains("dev");
  }

  public boolean isSSOProfile() {
    return Arrays.asList(environment.getActiveProfiles()).contains(SSO_AUTH_PROFILE);
  }

  public boolean isIAMProfile() {
    return Arrays.asList(environment.getActiveProfiles()).contains(IAM_AUTH_PROFILE);
  }

  public boolean currentProfileCanLogout() {
    return Arrays.stream(environment.getActiveProfiles())
        .noneMatch(CANT_LOGOUT_AUTH_PROFILES::contains);
  }
}
