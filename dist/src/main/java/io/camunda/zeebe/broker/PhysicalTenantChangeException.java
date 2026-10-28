/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A change of the physical tenant set that was not applied, or not applied completely. */
@NullMarked
public final class PhysicalTenantChangeException extends RuntimeException {

  private final Reason reason;

  public PhysicalTenantChangeException(
      final Reason reason, final String message, final @Nullable Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }

  public enum Reason {
    /** Another change is being applied; nothing was changed. */
    IN_PROGRESS,
    /** The change contradicts the current tenant set, e.g. the tenant already exists. */
    CONFLICT,
    /** The configuration fails the boot validations; nothing was changed. */
    INVALID,
    /** The change was accepted but the tenant module did not start; the tenant is FAILED. */
    FAILED
  }
}
