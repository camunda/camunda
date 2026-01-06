/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.reader;

import io.camunda.security.auth.Authorization;

/**
 * Enables or disables a {@link AuthorizationCheck}. If enabled, then the authorization to be
 * checked must be provided.
 */
public record AuthorizationCheck(boolean enabled, Authorization<?> authorization) {

  public static AuthorizationCheck enabled(final Authorization<?> authorization) {
    return new AuthorizationCheck(true, authorization);
  }

  public static AuthorizationCheck disabled() {
    return new AuthorizationCheck(false, null);
  }
}
