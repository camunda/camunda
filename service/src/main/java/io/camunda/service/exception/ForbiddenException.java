/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.exception;

import io.camunda.security.auth.Authorization;

public class ForbiddenException extends RuntimeException {

  private static final String UNAUTHORIZED_ERROR_MESSAGE =
      "Unauthorized to perform operation '%s' on resource '%s'";

  public ForbiddenException(final Authorization authorization) {
    super(
        UNAUTHORIZED_ERROR_MESSAGE.formatted(
            authorization.permissionType(), authorization.resourceType()));
  }
}
