/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import java.util.Set;

public interface TasklistProfileService {

  String SSO_AUTH_PROFILE = "sso-auth";
  String IDENTITY_AUTH_PROFILE = "identity-auth";
  String CONSOLIDATED_AUTH_PROFILE = "consolidated-auth";
  String AUTH_PROFILE = "auth";
  String DEFAULT_AUTH = AUTH_PROFILE;
  String LDAP_AUTH_PROFILE = "ldap-auth";
  Set<String> AUTH_PROFILES =
      Set.of(
          AUTH_PROFILE,
          LDAP_AUTH_PROFILE,
          SSO_AUTH_PROFILE,
          IDENTITY_AUTH_PROFILE,
          CONSOLIDATED_AUTH_PROFILE);

  String getMessageByProfileFor(Exception exception);

  boolean currentProfileCanLogout();

  boolean isLoginDelegated();
}
