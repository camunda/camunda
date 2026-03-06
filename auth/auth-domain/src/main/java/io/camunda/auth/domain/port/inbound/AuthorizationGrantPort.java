/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.port.inbound;

import io.camunda.auth.domain.model.AuthorizationGrantRequest;
import io.camunda.auth.domain.model.AuthorizationGrantResponse;

/**
 * Inbound port for performing authorization grant operations. Supports all OAuth2 grant types
 * modeled by the sealed {@link AuthorizationGrantRequest} hierarchy.
 */
public interface AuthorizationGrantPort {

  /**
   * Performs an authorization grant operation (token exchange, client credentials, JWT bearer, or
   * authorization code).
   *
   * @param request the authorization grant request
   * @return the authorization grant response containing the new token
   */
  AuthorizationGrantResponse authorize(AuthorizationGrantRequest request);
}
