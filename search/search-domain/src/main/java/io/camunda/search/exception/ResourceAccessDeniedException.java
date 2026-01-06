/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.exception;

import io.camunda.security.auth.Authorization;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.stream.Collectors;

public class ResourceAccessDeniedException extends CamundaSearchException {

  private final List<MissingAuthorization> missingAuthorizations;

  public ResourceAccessDeniedException(final MissingAuthorization missingAuthorization) {
    super(missingSingleAuthMessage(missingAuthorization), Reason.FORBIDDEN);
    this.missingAuthorizations = List.of(missingAuthorization);
  }

  public ResourceAccessDeniedException(final Authorization<?> authorization) {
    this(MissingAuthorization.from(authorization));
  }

  /**
   * Support multiple missing authorizations (e.g. when checking AnyOfAuthorizationCondition and
   * none of the branches are granted).
   */
  public ResourceAccessDeniedException(final List<Authorization<?>> authorizations) {
    super(missingMultipleAuthMessage(MissingAuthorization.from(authorizations)), Reason.FORBIDDEN);
    this.missingAuthorizations = MissingAuthorization.from(authorizations);
  }

  public List<MissingAuthorization> getMissingAuthorizations() {
    return missingAuthorizations;
  }

  private static String missingSingleAuthMessage(final MissingAuthorization missingAuthorization) {
    return "Unauthorized to perform operation '%s' on resource '%s'"
        .formatted(missingAuthorization.permissionType(), missingAuthorization.resourceType());
  }

  private static String missingMultipleAuthMessage(
      final List<MissingAuthorization> missingAuthorization) {
    if (missingAuthorization == null || missingAuthorization.isEmpty()) {
      // should not happen, but fallback to generic message
      return "Unauthorized to perform operation(s) on resource(s)";
    }

    if (missingAuthorization.size() == 1) {
      return missingSingleAuthMessage(missingAuthorization.getFirst());
    }

    final var parts =
        missingAuthorization.stream()
            .map(ma -> "('%s' on '%s')".formatted(ma.permissionType(), ma.resourceType()))
            .collect(Collectors.joining(", "));

    return "Unauthorized to perform any of the operations %s".formatted(parts);
  }

  public record MissingAuthorization(
      AuthorizationResourceType resourceType, PermissionType permissionType) {

    static MissingAuthorization from(final Authorization<?> authorization) {
      return new MissingAuthorization(authorization.resourceType(), authorization.permissionType());
    }

    static List<MissingAuthorization> from(final List<Authorization<?>> authorizations) {
      return authorizations.stream().map(MissingAuthorization::from).collect(Collectors.toList());
    }
  }
}
