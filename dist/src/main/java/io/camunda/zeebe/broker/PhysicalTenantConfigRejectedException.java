/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/** A change of a physical tenant's configuration that the cluster did not accept. */
@NullMarked
public final class PhysicalTenantConfigRejectedException extends RuntimeException {

  private final Status status;
  private final List<String> violations;

  public PhysicalTenantConfigRejectedException(
      final Status status, final String message, final List<String> violations) {
    super(message);
    this.status = status;
    this.violations = List.copyOf(violations);
  }

  static PhysicalTenantConfigRejectedException of(final ErrorResponse error) {
    final var status =
        switch (error.code()) {
          case INVALID_REQUEST, INVALID_STATE -> Status.INVALID;
          case NOT_FOUND -> Status.NOT_FOUND;
          case CONCURRENT_MODIFICATION -> Status.IN_PROGRESS;
          case OPERATION_NOT_ALLOWED -> Status.NOT_OWNER;
          case INTERNAL_ERROR -> Status.UNAVAILABLE;
        };
    return new PhysicalTenantConfigRejectedException(
        status, String.valueOf(error.message()), List.of());
  }

  public Status status() {
    return status;
  }

  /** Why the configuration is invalid, one entry per violation. */
  public List<String> violations() {
    return violations;
  }

  public enum Status {
    /** The configuration fails a validation; nothing changed. */
    INVALID,
    /** The tenant does not exist. */
    NOT_FOUND,
    /** Another tenant change runs; retry once it completed. */
    IN_PROGRESS,
    /** The tenant is owned by the other input. */
    NOT_OWNER,
    /** The coordinator could not be reached or failed. */
    UNAVAILABLE
  }
}
