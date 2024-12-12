/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.authentication.AuthProfile;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * A fixed set of Spring profiles. Some are application specific (broker, gateway), and others
 * environment specific (dev, test ,prod). Here to avoid littering the code base with different hard
 * coded strings, leading to potential errors.
 */
public enum Profile {
  // application specific profiles
  BROKER("broker"),
  GATEWAY("gateway"),
  RESTORE("restore"),
  OPERATE("operate"),
  OPTIMIZE("optimize"),
  TASKLIST("tasklist"),
  IDENTITY("identity"),

  // environment profiles
  TEST("test"),
  DEVELOPMENT("dev"),
  PRODUCTION("prod"),

  // authentication profiles
  AUTH_BASIC("auth-basic"),
  AUTH_BASIC_WITH_UNPROTECTED_API("auth-basic-with-unprotected-api"),
  AUTH_OIDC("auth-oidc"),
  IDENTITY_AUTH("identity-auth"),
  SSO_AUTH("sso-auth"),
  LDAP_AUTH_PROFILE("ldap-auth"),

  // migration profiles
  MIGRATION("migration"),
  IDENTITY_MIGRATION("identity-migration"),
  PROCESS_MIGRATION("process-migration"),
  // indicating legacy standalone application
  STANDALONE("standalone");

  private final String id;

  Profile(final String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public static Set<Profile> getAuthProfiles() {
    return Arrays.stream(AuthProfile.values())
        .map(authProfile -> get(authProfile.getId()))
        .collect(Collectors.toSet());
  }

  public static Set<Profile> getWebappProfiles() {
    return Set.of(TASKLIST, IDENTITY, OPERATE);
  }

  @Override
  public String toString() {
    return "Profiles{ordinal='" + ordinal() + "', name='" + name() + "', id='" + id + '\'' + "}";
  }

  public static @Nullable Profile get(final String id) {
    return Arrays.stream(Profile.values())
        .filter(profile -> profile.getId().equals(id))
        .findFirst()
        .orElse(null);
  }
}
