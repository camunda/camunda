/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.result;

import io.camunda.zeebe.engine.processing.Rejection;

/**
 * Represents a typed authorization failure which occurred while evaluating an authorization check.
 */
public record AuthorizationRejection(
    Rejection rejection, AuthorizationRejectionType authorizationRejectionType) {

  public static AuthorizationRejection ofPermission(final Rejection rejection) {
    return new AuthorizationRejection(rejection, AuthorizationRejectionType.PERMISSION);
  }

  public static AuthorizationRejection ofTenant(final Rejection rejection) {
    return new AuthorizationRejection(rejection, AuthorizationRejectionType.TENANT);
  }

  public boolean isPermission() {
    return AuthorizationRejectionType.PERMISSION == authorizationRejectionType;
  }

  public boolean isTenant() {
    return AuthorizationRejectionType.TENANT == authorizationRejectionType;
  }

  public enum AuthorizationRejectionType {
    /** The authenticated principal lacks the required permission on the requested resource. */
    PERMISSION,
    /** The authenticated principal is not assigned to the requested tenant. */
    TENANT
  }
}
