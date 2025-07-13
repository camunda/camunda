/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import io.camunda.security.auth.SecurityContext;
import io.camunda.security.reader.AuthorizationCheck;

/** Strategy to apply authorization to a search query. */
public interface AuthorizationQueryStrategy {

  AuthorizationQueryStrategy NONE = (securityContext) -> AuthorizationCheck.disabled();

  /**
   * Apply authorization to a search query.
   *
   * @param securityContext
   * @return the search query request with authorization applied
   */
  AuthorizationCheck resolveAuthorizationCheck(SecurityContext securityContext);
}
