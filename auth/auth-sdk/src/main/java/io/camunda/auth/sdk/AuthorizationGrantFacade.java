/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.sdk;

import io.camunda.auth.domain.model.AuthorizationGrantRequest;
import io.camunda.auth.domain.model.AuthorizationGrantResponse;

/** Public interface for authorization grant operations across all grant types. */
public interface AuthorizationGrantFacade {

  /**
   * Performs an authorization grant and returns the complete response.
   *
   * @param request the authorization grant request (any sealed subtype)
   * @return the authorization grant response
   */
  AuthorizationGrantResponse authorize(AuthorizationGrantRequest request);

  /** Returns whether authorization grants are available. */
  boolean isAvailable();
}
