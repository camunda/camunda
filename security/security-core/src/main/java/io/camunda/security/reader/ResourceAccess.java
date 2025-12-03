/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.reader;

import io.camunda.security.auth.Authorization;
import java.util.Objects;

public record ResourceAccess(boolean allowed, boolean wildcard, Authorization<?> authorization) {

  public ResourceAccess {
    Objects.requireNonNull(authorization, "Authorization must not be null");
  }

  /** Returns true if access to the resource is denied. */
  public boolean denied() {
    return !allowed;
  }

  /**
   * Creates a {@link ResourceAccess} that allows access to a specific resource. The resource and
   * the granted permissions are specified by the passed {@link Authorization authorization}.
   */
  public static ResourceAccess allowed(final Authorization<?> authorization) {
    return new ResourceAccess(true, false, authorization);
  }

  /**
   * Creates a {@link ResourceAccess} that denies access to a specific resource. The denied access
   * is specified by the passed {@link Authorization authorization}.
   */
  public static ResourceAccess denied(final Authorization<?> authorization) {
    return new ResourceAccess(false, false, authorization);
  }

  /**
   * Creates a {@link ResourceAccess} that allows wildcard access to any specific resource. The
   * passed {@link Authorization authorization} specifies the permissions that are allowed.
   */
  public static ResourceAccess wildcard(final Authorization<?> authorization) {
    return new ResourceAccess(true, true, authorization);
  }
}
