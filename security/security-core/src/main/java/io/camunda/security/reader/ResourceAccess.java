/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.reader;

import io.camunda.security.auth.Authorization;

public record ResourceAccess(boolean allowed, boolean wildcard, Authorization authorization) {

  public boolean denied() {
    return !allowed;
  }

  public static ResourceAccess allowed(final Authorization authorization) {
    return new ResourceAccess(true, false, authorization);
  }

  public static ResourceAccess denied(final Authorization authorization) {
    return new ResourceAccess(false, false, authorization);
  }

  public static ResourceAccess wildcard(final Authorization authorization) {
    return new ResourceAccess(true, true, authorization);
  }
}
