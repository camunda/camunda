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
  private static final String FORBIDDEN_MSG_WITH_RESOURCE_ID =
      FORBIDDEN_MSG + ", required resource identifiers are one of '[*, %s]'";
  private static final String TENANT_MSG =
      "Expected to access tenant '%s', but the principal is not authorized.";
  private static final String FORBIDDEN_MSG_WITH_PROPERTIES =
      FORBIDDEN_MSG + " or resource must match property constraints '[%s]'";

  private AuthorizationRejectionMapper() {}

  public static Rejection toRejection(final AuthorizationRejection rejection) {
    return switch (rejection) {
      case AuthorizationRejection.Tenant t ->
          new Rejection(RejectionType.FORBIDDEN, TENANT_MSG.formatted(t.tenantId()));
      case AuthorizationRejection.Permission p ->
          p.resourceId().equals("*")
              ? new Rejection(
                  RejectionType.FORBIDDEN,
                  FORBIDDEN_MSG.formatted(p.permissionType(), p.resourceType()))
              : new Rejection(
                  RejectionType.FORBIDDEN,
                  FORBIDDEN_MSG_WITH_RESOURCE_ID.formatted(
                      p.permissionType(), p.resourceType(), p.resourceId()));
      case AuthorizationRejection.Property p ->
          new Rejection(
              RejectionType.FORBIDDEN,
              FORBIDDEN_MSG_WITH_PROPERTIES.formatted(
                  p.permissionType(), p.resourceType(), String.join(", ", p.propertyNames())));
    };
  }

  /**
   * Like {@link #toRejection} but without the {@code required resource identifiers are one of '[*,
   * ...]'} suffix on permission rejections. Reproduces the pre-migration identity-processor denial
   * message (bare {@code "Insufficient permissions to perform operation '%s' on resource '%s'"}),
   * which the engine-internal path never enriched with resource ids. Tenant and property rejections
   * are mapped identically to {@link #toRejection}.
   */
  public static Rejection toBareRejection(final AuthorizationRejection rejection) {
    if (rejection instanceof final AuthorizationRejection.Permission p) {
      return forbidden(p.permissionType(), p.resourceType());
    }
    return toRejection(rejection);
  }

  /** Builds a FORBIDDEN rejection when no principal identity is present in the request. */
  public static Rejection noPrincipal() {
    return new Rejection(
        RejectionType.FORBIDDEN,
        "No authenticated user or client could be determined for the request.");
  }

  /** Builds a FORBIDDEN rejection with the standard insufficient-permissions message. */
  public static Rejection forbidden(final Object permissionType, final Object resourceType) {
    return new Rejection(
        RejectionType.FORBIDDEN, FORBIDDEN_MSG.formatted(permissionType, resourceType));
  }
}
