/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

public enum AuthProfile {
  BASIC("auth-basic"),
  BASIC_WITH_UNPROTECTED_API("auth-basic-with-unprotected-api"),
  OIDC("auth-oidc"),
  LDAP("ldap-auth");

  private final String id;

  AuthProfile(final String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }
}
