/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.security;

import io.camunda.security.auth.Authorization;

public record ResourceAccess(boolean granted, boolean wildcard, Authorization<?> authorization) {

  public boolean hasWildcardAccess() {
    return granted && wildcard;
  }

  public boolean forbidden() {
    return !granted;
  }

  public static ResourceAccess wildcard(final Authorization<?> authorization) {
    return new ResourceAccess(true, true, authorization);
  }

  public static ResourceAccess granted(final Authorization<?> authorization) {
    return new ResourceAccess(true, false, authorization);
  }

  public static ResourceAccess denied(final Authorization<?> authorization) {
    return new ResourceAccess(false, false, authorization);
  }
}
