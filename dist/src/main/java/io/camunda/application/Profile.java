/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import java.util.Set;

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
  TASKLIST("tasklist"),
  IDENTITY("identity"),

  // environment profiles
  TEST("test"),
  DEVELOPMENT("dev"),
  PRODUCTION("prod"),

  // authentication profiles
  CONSOLIDATED_AUTH("consolidated-auth"),
  IDENTITY_AUTH("identity-auth"),
  SSO_AUTH("sso-auth"),
  DEFAULT_AUTH_PROFILE("auth"),
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
    return Set.of(
        CONSOLIDATED_AUTH, DEFAULT_AUTH_PROFILE, IDENTITY_AUTH, LDAP_AUTH_PROFILE, SSO_AUTH);
  }

  public static Set<Profile> getWebappProfiles() {
    return Set.of(TASKLIST, IDENTITY, OPERATE);
  }

  @Override
  public String toString() {
    return "Profiles{ordinal='" + ordinal() + "', name='" + name() + "', id='" + id + '\'' + "}";
  }
}
