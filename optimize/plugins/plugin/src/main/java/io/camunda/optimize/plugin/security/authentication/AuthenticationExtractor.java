/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.plugin.security.authentication;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthenticationExtractor {

  /**
   * Checks the request for authentication. May not return null, but always an AuthenticationResult
   * that indicates, whether authentication was successful, and, if true, always provides the
   * authenticated user.
   *
   * @param servletRequest the request to authenticate
   */
  AuthenticationResult extractAuthenticatedUser(HttpServletRequest servletRequest);
}
