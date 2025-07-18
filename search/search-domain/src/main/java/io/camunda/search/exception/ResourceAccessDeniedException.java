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

public class ResourceAccessDeniedException extends CamundaSearchException {

  private final MissingAuthorization missingAuthorization;

  public ResourceAccessDeniedException(final MissingAuthorization missingAuthorization) {
    super(
        "Unauthorized to perform operation '%s' on resource '%s'"
            .formatted(missingAuthorization.permissionType(), missingAuthorization.resourceType()),
        Reason.FORBIDDEN);
    this.missingAuthorization = missingAuthorization;
  }

  public ResourceAccessDeniedException(final Authorization authorization) {
    this(MissingAuthorization.from(authorization));
  }

  public MissingAuthorization getMissingAuthorization() {
    return missingAuthorization;
  }

  public record MissingAuthorization(
      AuthorizationResourceType resourceType, PermissionType permissionType) {

    static MissingAuthorization from(final Authorization authorization) {
      return new MissingAuthorization(authorization.resourceType(), authorization.permissionType());
    }
  }
}
