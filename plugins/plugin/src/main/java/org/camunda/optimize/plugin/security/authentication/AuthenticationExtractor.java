/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin.security.authentication;

import javax.servlet.http.HttpServletRequest;

public interface AuthenticationExtractor {

  /**
   * Checks the request for authentication. May not return null, but always
   * an AuthenticationResult that indicates, whether
   * authentication was successful, and, if true, always provides the authenticated user.
   *
   * @param servletRequest the request to authenticate
   */
  AuthenticationResult extractAuthenticatedUser(HttpServletRequest servletRequest);
}
