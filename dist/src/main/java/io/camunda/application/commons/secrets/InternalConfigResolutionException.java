/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import org.jspecify.annotations.Nullable;

/** A configuration reference of a physical tenant could not be resolved. */
public final class InternalConfigResolutionException extends RuntimeException {

  public InternalConfigResolutionException(final String message) {
    super(message);
  }

  public InternalConfigResolutionException(final String message, final @Nullable Throwable cause) {
    super(message, cause);
  }
}
