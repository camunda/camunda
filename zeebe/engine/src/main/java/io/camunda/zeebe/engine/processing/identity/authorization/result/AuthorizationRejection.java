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
public sealed interface AuthorizationRejection
    permits AuthorizationRejection.Tenant, AuthorizationRejection.Permission {

  default boolean isTenant() {
    return false;
  }

  default boolean isPermission() {
    return false;
  }

  Rejection rejection();

  record Tenant(Rejection rejection) implements AuthorizationRejection {

    @Override
    public boolean isTenant() {
      return true;
    }
  }

  record Permission(Rejection rejection) implements AuthorizationRejection {

    @Override
    public boolean isPermission() {
      return true;
    }
  }
}
