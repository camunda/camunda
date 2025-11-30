/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.exception;

import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior.AuthorizationRequestMetadata;
import io.camunda.zeebe.protocol.record.RejectionType;

/**
 * Signals that an authorization check failed, provides a user-facing error message derived from the
 * given authorization request metadata.
 */
public class ForbiddenException extends RuntimeException {

  public ForbiddenException(final AuthorizationRequest authRequest) {
    this(authRequest.build());
  }

  public ForbiddenException(final AuthorizationRequestMetadata authRequest) {
    super(authRequest.getForbiddenErrorMessage());
  }

  public RejectionType getRejectionType() {
    return RejectionType.FORBIDDEN;
  }
}
