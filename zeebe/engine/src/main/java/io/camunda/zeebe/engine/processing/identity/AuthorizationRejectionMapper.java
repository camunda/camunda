/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.security.api.model.authz.AuthorizationRejection;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.protocol.record.RejectionType;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class AuthorizationRejectionMapper {

  private static final String FORBIDDEN_MSG =
      "Insufficient permissions to perform operation '%s' on resource '%s'";
  private static final String TENANT_MSG =
      "Expected to access tenant '%s', but the principal is not authorized.";

  private AuthorizationRejectionMapper() {}

  public static Rejection toRejection(final AuthorizationRejection rejection) {
    return switch (rejection) {
      case AuthorizationRejection.Tenant t ->
          new Rejection(RejectionType.FORBIDDEN, TENANT_MSG.formatted(t.tenantId()));
      case AuthorizationRejection.Permission p ->
          new Rejection(
              RejectionType.FORBIDDEN,
              FORBIDDEN_MSG.formatted(p.permissionType(), p.resourceType()));
    };
  }
}
