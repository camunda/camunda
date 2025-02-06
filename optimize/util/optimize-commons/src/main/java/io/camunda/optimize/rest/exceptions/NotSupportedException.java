/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class NotSupportedException extends ResponseStatusException {

  public NotSupportedException(final String reason) {
    super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, reason);
  }

  public NotSupportedException(final String reason, final Throwable cause) {
    super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, reason, cause);
  }

  @Override
  public String getMessage() {
    return getReason() != null ? getReason() : "";
  }
}
